/*
 * Copyright 2010, 2011 Colin Paton - cozzarp@googlemail.com
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

package org.redbus.ui.stopmap;

import java.util.*;

import android.support.v4.app.FragmentActivity;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;
import org.redbus.R;
import org.redbus.geocode.GeocodingHelper;
import org.redbus.geocode.IGeocodingResponseListener;
import org.redbus.stopdb.ServiceBitmap;
import org.redbus.stopdb.StopDbHelper;
import org.redbus.ui.BusyDialog;
import org.redbus.ui.Common;
import org.redbus.ui.arrivaltime.ArrivalTimeActivity;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.location.Address;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.Toast;

public class StopMapActivity extends FragmentActivity implements IGeocodingResponseListener, OnCancelListener, GoogleMap.OnCameraChangeListener {
    private static final String TAG = "StopMapActivity";

    private GoogleMap map;
	// private StopMapOverlay stopOverlay;
	private ServiceBitmap serviceFilter = new ServiceBitmap();

	private BusyDialog busyDialog = null;
	private int expectedRequestId = -1;
	
	private final int StopTapRadiusMetres = 50;
	private boolean isFirstResume = true;

    private Map<Integer, Marker> visibleMarkers = new HashMap<Integer, Marker>();

    private StopDbHelper pointTree;

    private BitmapDescriptor unknownStopBitmap;
    private BitmapDescriptor outoforderStopBitmap;
    private BitmapDescriptor divertedStopBitmap;
    private BitmapDescriptor nStopBitmap;
    private BitmapDescriptor neStopBitmap;
    private BitmapDescriptor eStopBitmap;
    private BitmapDescriptor seStopBitmap;
    private BitmapDescriptor sStopBitmap;
    private BitmapDescriptor swStopBitmap;
    private BitmapDescriptor wStopBitmap;
    private BitmapDescriptor nwStopBitmap;
	
	public static void showActivity(Context context, 
			int lat,
			int lng) {
		Intent i = new Intent(context, StopMapActivity.class);
		i.putExtra("Lat", lat);
		i.putExtra("Lng", lng);
		context.startActivity(i);
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) { 
		super.onCreate(savedInstanceState);
		setContentView(R.layout.stop_map);
		busyDialog = new BusyDialog(this);

        this.pointTree = StopDbHelper.Load(this);

        nStopBitmap = BitmapDescriptorFactory.fromResource(R.drawable.compass_n);
        neStopBitmap = BitmapDescriptorFactory.fromResource(R.drawable.compass_ne);
        eStopBitmap = BitmapDescriptorFactory.fromResource(R.drawable.compass_e);
        seStopBitmap = BitmapDescriptorFactory.fromResource(R.drawable.compass_se);
        sStopBitmap = BitmapDescriptorFactory.fromResource(R.drawable.compass_s);
        swStopBitmap = BitmapDescriptorFactory.fromResource(R.drawable.compass_sw);
        wStopBitmap = BitmapDescriptorFactory.fromResource(R.drawable.compass_w);
        nwStopBitmap = BitmapDescriptorFactory.fromResource(R.drawable.compass_nw);
        unknownStopBitmap = BitmapDescriptorFactory.fromResource(R.drawable.stop_unknown);
        outoforderStopBitmap = BitmapDescriptorFactory.fromResource(R.drawable.stop_outoforder);
        divertedStopBitmap = BitmapDescriptorFactory.fromResource(R.drawable.stop_diverted);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        map = mapFragment.getMap();
        if (map != null) {
            map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            map.setMyLocationEnabled(true);

            // mapController = mapView.getController();
            // mapController.setZoom(17);

            // Make map update automatically as user moves around
            // myLocationOverlay = new ReallyMyLocationOverlay(this, mapView);
            // mapView.getOverlays().add(myLocationOverlay);

            // Check to see if we've been passed data
            int lat = getIntent().getIntExtra("Lat", -1);
            int lng = getIntent().getIntExtra("Lng", -1);

            if (lat == -1 || lng == -1) {
                Log.d(TAG, "Not supplied with either lat or lng");
                // if we don't have a location supplied, try and use the last known one.
                LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
                Location gpsLocation = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                Location networkLocation = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

                if ((gpsLocation != null) && (gpsLocation.getAccuracy() < 100)) {
                    Log.d(TAG, "Using GPS for location. " + gpsLocation);

                    zoomTo(new LatLng(gpsLocation.getLatitude(), gpsLocation.getLongitude()));
                    // mapController.setCenter(new GeoPoint((int) (gpsLocation.getLatitude() * 1000000), (int) (gpsLocation.getLongitude() * 1000000)));
                } else if ((networkLocation != null) && (networkLocation.getAccuracy() < 100)) {
                    Log.d(TAG, "Using network for location.");
                    // mapController.setCenter(new GeoPoint((int) (networkLocation.getLatitude() * 1000000), (int) (networkLocation.getLongitude() * 1000000)));
                    zoomTo(new LatLng(networkLocation.getLatitude(), networkLocation.getLongitude()));

                } else {
                    Log.d(TAG, "Using default location from db.");
                    StopDbHelper stopDb = StopDbHelper.Load(this);
                    zoomTo(new LatLng(stopDb.defaultMapLocationLat / 1E6, stopDb.defaultMapLocationLon / 1E6));
                    // mapController.setCenter(new GeoPoint(stopDb.defaultMapLocationLat, stopDb.defaultMapLocationLon));
                }
                updateMyLocationStatus(true);
            } else {
                Log.d(TAG, "Using supplied lat and lng.");
                zoomTo(new LatLng(lat / 1E6, lng / 1E6));
                // mapController.setCenter(new GeoPoint(lat, lng));
                updateMyLocationStatus(false);
            }

            map.setOnCameraChangeListener(this);

            // stopOverlay = new StopMapOverlay(this);
            // mapView.getOverlays().add(stopOverlay);
        }
	}

    @Override
    public void onCameraChange(CameraPosition pos) {
        // Get the visible bounds:
        updateMap();
    }

    public void updateMap() {
        // Add the stops:
        /* Marker m = map.addMarker(new MarkerOptions()
                .position(KIEL)
                .title("Kiel")
                .snippet("Kiel is cool"));
                */
        LatLngBounds bounds = this.map.getProjection().getVisibleRegion().latLngBounds;

        for (Marker m: visibleMarkers.values()) {
            if (!bounds.contains(m.getPosition())) {
                Log.d(TAG, "Removing: " + m.toString());
                m.remove();
            }
        }

        /*.icon(BitmapDescriptorFactory
                            .fromResource(R.drawable.compass_e)));*/
        addMarkers(pointTree.rootRecordNum, 0,
                (int)(bounds.northeast.latitude * 1E6),
                (int)(bounds.northeast.longitude * 1E6),
                (int)(bounds.southwest.latitude * 1E6),
                (int)(bounds.southwest.longitude * 1E6));
    }

    public void addMarkers(int stopNodeIdx, int depth,
                           int lat_tl, int lon_tl, int lat_br, int lon_br) {
        Log.d(TAG, "addMarkers: " + lat_tl + ", " + lon_tl + ", " + lat_br + ", " + lon_br);
        int tl, br, here, lat, lon;

        if (stopNodeIdx==-1) {
            return;
        }

        lat=pointTree.lat[stopNodeIdx];
        lon=pointTree.lon[stopNodeIdx];

        if (depth % 2 == 0) {
            here = lat;
            tl = lat_tl;
            br = lat_br;
        }
        else {
            here = lon;
            tl = lon_tl;
            br = lon_br;
        }

        if (tl > br) {
            Log.println(Log.ERROR,"redbus", "co-ord error!");
        }

        if (br > here)
            addMarkers(pointTree.right[stopNodeIdx],depth+1,
                    lat_tl, lon_tl, lat_br, lon_br);

        if (tl < here)
            addMarkers(pointTree.left[stopNodeIdx],depth+1,
                    lat_tl, lon_tl, lat_br, lon_br);

        if (visibleMarkers.containsKey(stopNodeIdx)) {
            return;
        }

        Log.d(TAG, "LatLon: " + lat + ", " + lon);
        Log.d(TAG, "  Left? " + (lat_tl <= lat) + ", Right? " + (lat_br >= lat));
        Log.d(TAG, "  Above? " + (lon_tl <= lon) + " Below? " + (lon_br >= lon));

        // If this node falls within range, add it
        if (lat_tl <= lat && lat_br >= lat && lon_tl <= lon && lon_br >= lon) {
            boolean validServices = ((pointTree.serviceMap0[stopNodeIdx] & serviceFilter.bits0) != 0) ||
                    ((pointTree.serviceMap1[stopNodeIdx] & serviceFilter.bits1) != 0);

            BitmapDescriptor bmp = unknownStopBitmap;
            switch(pointTree.facing[stopNodeIdx]) {
                case StopDbHelper.STOP_FACING_N:
                    bmp = nStopBitmap;
                    break;
                case StopDbHelper.STOP_FACING_NE:
                    bmp = neStopBitmap;
                    break;
                case StopDbHelper.STOP_FACING_E:
                    bmp = eStopBitmap;
                    break;
                case StopDbHelper.STOP_FACING_SE:
                    bmp = seStopBitmap;
                    break;
                case StopDbHelper.STOP_FACING_S:
                    bmp = sStopBitmap;
                    break;
                case StopDbHelper.STOP_FACING_SW:
                    bmp = swStopBitmap;
                    break;
                case StopDbHelper.STOP_FACING_W:
                    bmp = wStopBitmap;
                    break;
                case StopDbHelper.STOP_FACING_NW:
                    bmp = nwStopBitmap;
                    break;
                case StopDbHelper.STOP_OUTOFORDER:
                    bmp = outoforderStopBitmap;
                    break;
                case StopDbHelper.STOP_DIVERTED:
                    bmp = divertedStopBitmap;
                    break;
            }


            Marker m = map.addMarker(new MarkerOptions()
                    .icon(bmp)
                    .position(new LatLng(pointTree.lat[stopNodeIdx]/1E6, pointTree.lat[stopNodeIdx]/1E6)));
            // Add marker here
            Log.d(TAG, "Adding: " + m.toString());
            visibleMarkers.put(stopNodeIdx, m);
        }
    }
	
	public void invalidate()
	{
		// this.stopOverlay.invalidate();
		//this.mapView.invalidate();
	}
	
	public ServiceBitmap getServiceFilter() {
		return serviceFilter;
	}

	@Override
	public void onPause() {
		updateMyLocationStatus(false);
		// stopOverlay.onPause();
		super.onPause();
	}

	@Override
	public void onResume() {
		if (!isFirstResume)
			updateMyLocationStatus(true);
		isFirstResume = false;
		super.onResume();
	}	
	
	@Override
	protected void onDestroy() {
		if (busyDialog != null)
			busyDialog.dismiss();
		busyDialog = null;
		super.onDestroy();
	}

	public void onCancel(DialogInterface dialog) {
		expectedRequestId = -1;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.stopmap_menu, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
        if (this.map.getMapType() == GoogleMap.MAP_TYPE_NORMAL)
			menu.findItem(R.id.stopmap_menu_satellite_or_map).setTitle("Satellite View");
		else
			menu.findItem(R.id.stopmap_menu_satellite_or_map).setTitle("Map View");
		
		if (serviceFilter.areAllSet)
			menu.findItem(R.id.stopmap_menu_showall).setEnabled(false);
		else
			menu.findItem(R.id.stopmap_menu_showall).setEnabled(true);
		
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.stopmap_menu_search:
			doSearchForLocation();
			return true;

		case R.id.stopmap_menu_showall:
			doShowAllServices();
			return true;

		case R.id.stopmap_menu_filterservices:
			doFilterServices();
			return true;

		case R.id.stopmap_menu_satellite_or_map:
			doSetMapType();
			return true;
			
		case R.id.stopmap_menu_mylocation:
			doSetMyLocation();
			return true;
		}
		
		return false;
	}
/*
	public boolean onStopMapTap(GeoPoint point, MapView mapView)
	{
		StopDbHelper pt = StopDbHelper.Load(this);
		final int nearestStopNodeIdx = pt.findNearest(point.getLatitudeE6(), point.getLongitudeE6());
		final int stopCode = pt.stopCode[nearestStopNodeIdx];
		final double stopLat = pt.lat[nearestStopNodeIdx] / 1E6;
		final double stopLon = pt.lon[nearestStopNodeIdx] / 1E6;

		// Yuk - there must be a better way to convert GeoPoint->Point than this?
		Location touchLoc = new Location("");
		touchLoc.setLatitude(point.getLatitudeE6() / 1E6);
		touchLoc.setLongitude(point.getLongitudeE6() / 1E6);

		Location stopLoc = new Location("");
		stopLoc.setLatitude(stopLat);
		stopLoc.setLongitude(stopLon);

		if (touchLoc.distanceTo(stopLoc) >= StopTapRadiusMetres)
			return false;

		new StopMapPopup(this, stopCode);
		return true;
	}*/
	
	public boolean onStopMapTouchEvent(MotionEvent e, MapView mapView) {
		// disable my location if user drags the map
		if (e.getAction() == MotionEvent.ACTION_MOVE)
			updateMyLocationStatus(false);			
		return false;
	}

	private void doSearchForLocation() {
		final EditText input = new EditText(this);
		new AlertDialog.Builder(this)
			.setTitle("Enter a location or postcode")
			.setView(input)
			.setPositiveButton(android.R.string.ok,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int whichButton) {
							busyDialog.show(StopMapActivity.this, "Finding location...");
							StopMapActivity.this.expectedRequestId = GeocodingHelper.geocode(StopMapActivity.this, input.getText().toString(), StopMapActivity.this);
						}
					})
			.setNegativeButton(android.R.string.cancel, null)
			.show();
	}
	
	private void doShowAllServices() {
		serviceFilter.setAll();
		StopMapActivity.this.invalidate();
	}
	
	private void doFilterServices() {
		final EditText input = new EditText(this);

		new AlertDialog.Builder(this)
			.setTitle("Enter services separated by spaces")
			.setView(input)
			.setPositiveButton(android.R.string.ok, 
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						ServiceBitmap filter = new ServiceBitmap().clearAll();
						StopDbHelper pt = StopDbHelper.Load(StopMapActivity.this);
						for(String serviceStr: input.getText().toString().split("[ ]+")) {
							if (pt.serviceNameToServiceBit.containsKey(serviceStr.toUpperCase()))
								filter.setBit(pt.serviceNameToServiceBit.get(serviceStr.toUpperCase()));
						}
						
						updateServiceFilter(filter);
					}
				})
			.setNegativeButton(android.R.string.cancel, null)
			.show();
	}
	
	public void doFilterServices(int stopCode) {
		StopDbHelper pt = StopDbHelper.Load(this);		
		int nodeIdx = pt.lookupStopNodeIdxByStopCode(stopCode);
		if (nodeIdx == -1)
			return;
		
		updateServiceFilter(pt.lookupServiceBitmapByStopNodeIdx(nodeIdx));
	}
	
	private void doSetMapType() {
        map.setMapType(map.getMapType() == GoogleMap.MAP_TYPE_NORMAL ? GoogleMap.MAP_TYPE_SATELLITE : GoogleMap.MAP_TYPE_NORMAL);
	}
	
	private void doSetMyLocation() {
		updateMyLocationStatus(true);
	}
	
	public void doStreetView(int stopCode) {
		StopDbHelper pt = StopDbHelper.Load(this);		
		int nodeIdx = pt.lookupStopNodeIdxByStopCode(stopCode);
		if (nodeIdx == -1)
			return;
		double stopLat = pt.lat[nodeIdx] / 1E6;
		double stopLon = pt.lon[nodeIdx] / 1E6;

		try {
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("google.streetview:cbll=" + stopLat + "," + stopLon + "&cbp=1,180,,0,2.0")));
		} catch (ActivityNotFoundException ex) {
			new AlertDialog.Builder(this)
			.setTitle("Google StreetView required")
			.setMessage("You will need Google StreetView installed for this to work. Would you like to go to the Android Market to install it?")
			.setPositiveButton(android.R.string.ok, 
					new DialogInterface.OnClickListener() {
	                    public void onClick(DialogInterface dialog, int whichButton) {
	                    	try {
	                    		startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.street")));
	                    	} catch (Throwable t) {
	                    		Toast.makeText(StopMapActivity.this, "Sorry, I couldn't find the Android Market either!", 5000).show();
	                    	}
	                    }
					})
			.setNegativeButton(android.R.string.cancel, null)
	        .show();
		}
	}
	
	public void doShowArrivalTimes(int stopCode) {
		ArrivalTimeActivity.showActivity(StopMapActivity.this, stopCode);
	}
	
	public void doAddBookmark(int stopCode) {
		StopDbHelper pt = StopDbHelper.Load(this);		
		int nodeIdx = pt.lookupStopNodeIdxByStopCode(stopCode);
		if (nodeIdx == -1)
			return;
		String stopName = pt.lookupStopNameByStopNodeIdx(nodeIdx);

		Common.doAddBookmark(this, stopCode, stopName);
	}
	
	private void updateServiceFilter(ServiceBitmap filter) {
		serviceFilter.setTo(filter);
		// mapController.setZoom(12);
		StopMapActivity.this.invalidate();		
	}
	
	private void updateMyLocationStatus(boolean status) {
		if (status) {
			// myLocationOverlay.enableMyLocation();
			Toast.makeText(this, "Finding your location...", Toast.LENGTH_SHORT).show();
		} else {
			// myLocationOverlay.disableMyLocation();
		}
	}	

	
	
	
	public void onAsyncGeocodeResponseError(int requestId, String message) {
        Log.w(TAG, "Geocode response error!");
		if (requestId != expectedRequestId)
			return;
		
		if (busyDialog != null)
			busyDialog.dismiss();
		
		new AlertDialog.Builder(this).setTitle("Error").
			setMessage("Unable to find location: " + message).
			setPositiveButton(android.R.string.ok, null).
			show();
	}

    public void zoomTo(LatLng pos) {
        Log.i(TAG, "Zooming to: " + pos);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 17));
    }

	public void onAsyncGeocodeResponseSucccess(int requestId, List<Address> addresses_) {
        Log.i(TAG, "Async Geocode success!");
		if (requestId != expectedRequestId)
			return;
		
		if (busyDialog != null)
			busyDialog.dismiss();
		
		if (addresses_.size() == 1) {
			Address address = addresses_.get(0);
            LatLng pos = new LatLng((int) (address.getLatitude() * 1E6), (int) (address.getLongitude() * 1E6));
            zoomTo(pos);
			return;
		}
		
		final List<Address> addresses = addresses_;
		ArrayList<String> addressNames = new ArrayList<String>();
		for(Address a: addresses) {
			StringBuilder strb = new StringBuilder();
			for(int i =0; i< a.getMaxAddressLineIndex(); i++) {
				if (i > 0)
					strb.append(", ");
				strb.append(a.getAddressLine(i));
			}
			addressNames.add(strb.toString());
		}

		new AlertDialog.Builder(this)
  	       .setSingleChoiceItems(addressNames.toArray(new String[addressNames.size()]), -1, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					if (which < 0)
						return;
					
					Address address = addresses.get(which);
                    LatLng pos = new LatLng((int) (address.getLatitude() * 1E6), (int) (address.getLongitude() * 1E6));
                    zoomTo(pos);
					dialog.dismiss();
				}
  	       })
  	       .show();
	}
}
	