/*
 * Copyright 2010 Colin Paton - cozzarp@googlemail.com
 * This file is part of rEdBus.
 *
 *  rEdBus is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  rEdBus is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with rEdBus.  If not, see <http://www.gnu.org/licenses/>.
 */

// KD-Tree implementation
// FIXME - Make into a generic KD-Tree class
// FIXME - currently a bit nasty due to way data is read from resource - e.g. needs nodes[] array
//       - Android only gives an InputStream to a resource - ideally we need Random access.
//       - Read data into memory for now.

package org.redbus;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.Math;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

import android.util.Log;


public class PointTree {

	// Represents a node in the tree
	public class BusStopTreeNode {
		private double x;
		private double y;
		private int leftNode;
		private int rightNode;
		private int stopCode;
		private String stopName;
		private long servicesMap;
		
		public BusStopTreeNode(DataInputStream inputStream) throws IOException
		{
			this.leftNode = inputStream.readInt();
			this.rightNode = inputStream.readInt();
			this.x = inputStream.readDouble();
			this.y = inputStream.readDouble();
			this.stopCode = inputStream.readInt();	
			
			byte[] b = new byte[16];
			inputStream.read(b,0,16); // Read name
			this.stopName = new String(b);

			this.servicesMap = inputStream.readLong();
		}
		
		public String getStopName() { return this.stopName; }
		public int getStopCode() { return this.stopCode; }
		public double getX() { return this.x; }
		public double getY() { return this.y; }
		public long getServicesMap() { return this.servicesMap; }
	}
	
	private BusStopTreeNode[] nodes;
	private Map<Integer, Integer> nodeIdxByStopCode;
	private String[] services;
	private int rootRecordNum;

	// Read Data from the Android resource 'stops.dat' into memory
	
	public PointTree(InputStream file) throws IOException
	{
		DataInputStream is = new DataInputStream(file);
		
		byte[] b = new byte[4];
        
		is.read(b,0,4); // Header version
		this.rootRecordNum = is.readInt();
			
		// Root is always the last record in the file
		this.nodes = new BusStopTreeNode[rootRecordNum+1];
		this.nodeIdxByStopCode = new HashMap<Integer, Integer>();

		for(int i = 0; i <= rootRecordNum; ++i)
		{
			BusStopTreeNode node = new BusStopTreeNode(is);
			nodes[i] = node;
			nodeIdxByStopCode.put(new Integer(node.getStopCode()), new Integer(i));
		}
		
		int servicesCount = is.readInt();
		this.services = new String[servicesCount];
		byte[] tmp = new byte[10];
		for(int i =0; i< servicesCount; i++) {
			int charIdx = 0;
			while(true) {
				int c = is.readByte();
				if (c == 0)
					break;
				tmp[charIdx++] = (byte) c;
			}
			this.services[i] = new String(tmp, 0, charIdx);
		}
	}
	
	// Could use Android location class to do this, but kept in here from prototype.
	// sqrt isn't technically needed, but in here to possibly do distance conversions later.
	
	private double distance(BusStopTreeNode node, double x, double y)
	{
		return Math.sqrt(Math.pow(node.getX()-x,2) + Math.pow(node.getY()-y,2));
	}
	
	private BusStopTreeNode lookupNode(int number)
	{
		if (number == -1) return null; // Child is a leaf
		
		return this.nodes[number];
	}
	
	// Recursive search - use 'findNearest' to start
	private BusStopTreeNode searchNearest(BusStopTreeNode here, BusStopTreeNode best, double x, double y, int depth)
	{
		if (here == null) return best;
		if (best == null) best = here;
		
		double herepos, wantedpos;
		
		if (depth % 2 == 0) {
		    herepos = here.getX();
		    wantedpos = x;
		}
		else {
			herepos = here.getY();
			wantedpos = y;
		}
		
		// Which is closer?
		double disthere = distance(here,x,y);
		double distbest = distance(best,x,y);
		
		if (disthere < distbest) best = here;
		
		// Which branch is nearer?
		BusStopTreeNode nearest, furthest;
		
		if (wantedpos < herepos) {
			nearest = lookupNode(here.leftNode);
			furthest = lookupNode(here.rightNode);
		}
		else{
			furthest = lookupNode(here.leftNode);
			nearest = lookupNode(here.rightNode);
		}
		
		best = searchNearest(nearest,best,x,y,depth+1);
		
		// Do we still need to search the away branch?
		distbest = distance(best,x,y);
		
		double distaxis = Math.abs(wantedpos - herepos);
		
		if (distaxis < distbest)
			best = searchNearest(furthest,best,x,y,depth+1);
		
		return best;
	}
	
	private BusStopTreeNode getRoot()
	{
		return this.nodes[this.rootRecordNum];
	}
	
	// Public interface to this class - finds the node nearest to the supplied
	// co-ords
	
	public BusStopTreeNode findNearest(double x, double y)
	{	
		return this.searchNearest(this.getRoot(),null,x,y,0);
	}
	
	private ArrayList<BusStopTreeNode> searchRect(double xtl, double ytl,
			                                         double xbr, double ybr,
			                                         BusStopTreeNode here,
			                                         ArrayList<BusStopTreeNode> stops,
			                                         int depth)
	{
		if (here==null) return stops;
	
		//Log.println(Log.DEBUG, "visiting", here.getStopName());
		
		double topleft, bottomright, herepos, herex, herey;
		
		herex=here.getX();
		herey=here.getY();
		
		if (depth % 2 == 0) {
		    herepos = herex;
		    topleft = xtl;
		    bottomright = xbr;
		}
		else {
			herepos = herey;
			topleft = ytl;
			bottomright = ybr;
		}
		
		if (topleft > bottomright) {
			Log.println(Log.ERROR,"redbus", "co-ord error!");
		}
		
		if (bottomright > herepos) {
			stops = searchRect(xtl,ytl,xbr,ybr,lookupNode(here.rightNode),stops, depth+1);
		}
		
		if (topleft < herepos) {
			stops = searchRect(xtl,ytl,xbr,ybr,lookupNode(here.leftNode),stops, depth+1);
		}
		
		// If this node falls within range, add it
		if (xtl <= herex && xbr >= herex && ytl <= herey && ybr >= herey) {
			stops.add(here);
		}
		
		return stops;
	}
	
	// Return nodes within a certain rectangle - top-left/bottom-right
	public ArrayList<BusStopTreeNode> findRect(double xtl, double ytl,
	                                             double xbr, double ybr)
	{
		ArrayList<BusStopTreeNode> stops = new ArrayList<BusStopTreeNode>();
		
		//Log.println(Log.DEBUG, "redbus", "tl: "+ Double.toString(xtl) + "," + Double.toString(ytl));
		//Log.println(Log.DEBUG, "redbus", "br: "+ Double.toString(xbr) + "," + Double.toString(ybr));
		
		return searchRect(xtl,ytl,xbr,ybr,this.getRoot(),stops,0);
	}
	
	// Return nodes within a certain radius
	public ArrayList<BusStopTreeNode> findRadius(double xcentre, double ycentre, double radiusMetres)
	{
		// Convert radius in metres to approximate decimal degrees.
		// http://en.wikipedia.org/wiki/Decimal_degrees
		//
		// 111,319.9 metres = roughly 1 degree
		
		double radiusDegrees = radiusMetres / 111319.9;
		
	    // A rectangle is *nearly* a circle, right...
		// Rectangle searching of the tree is easier than radius searching. We should
		// filter out finds outside the circle, but we're only interested in an approximate
		// search right now.
		
		return findRect(xcentre-radiusDegrees,
				          ycentre-radiusDegrees,
				          xcentre+radiusDegrees,
				          ycentre+radiusDegrees);
	}
	
	public BusStopTreeNode lookupStopByStopCode(int stopCode)
	{
		Integer node = nodeIdxByStopCode.get(new Integer(stopCode));
		if (node == null)
			return null;
		if (node.intValue() >= nodes.length)
			return null;
		return nodes[node.intValue()];
	}
	
	public ArrayList<String> lookupServices(long servicesMap)
	{
		ArrayList<String> result = new ArrayList<String>();
		for(int i=0; i< 64; i++)
			if ((servicesMap & (1 << i)) != 0)
				result.add(services[i]);
		return result;
	}
}
