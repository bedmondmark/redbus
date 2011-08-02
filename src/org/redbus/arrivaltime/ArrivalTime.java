/*
 * Copyright 2010, 2011 Andrew De Quincey -  adq@lidskialf.net
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

package org.redbus.arrivaltime;

public class ArrivalTime 
{	
	public String service;
	public int baseService;
	public String destination;
	public boolean lowFloorBus;
	public boolean isDiverted;
	public boolean arrivalEstimated;
	public boolean arrivalIsDue;
	public int arrivalMinutesLeft;
	public String arrivalAbsoluteTime;
	
	public int arrivalSortingIndex;

	
	public ArrivalTime(String service, String destination, boolean isDiverted, boolean lowFloorBus, boolean arrivalEstimated, boolean arrivalIsDue, int arrivalMinutesLeft, String arrivalAbsoluteTime) 
	{
		this.service = service;
		this.baseService = Integer.parseInt(service.replaceAll("[^0-9]", "").trim());
		this.destination = destination;
		this.isDiverted = isDiverted;
		this.lowFloorBus = lowFloorBus;
		this.arrivalEstimated = arrivalEstimated;
		this.arrivalIsDue = arrivalIsDue;
		this.arrivalMinutesLeft = arrivalMinutesLeft;
		this.arrivalAbsoluteTime = arrivalAbsoluteTime;
		
		if (isDiverted) {
			arrivalSortingIndex = 1000000;
		} else {
			if (arrivalIsDue) {
				arrivalSortingIndex = 0;
			} else if (arrivalMinutesLeft > 0) {
				arrivalSortingIndex = arrivalMinutesLeft;
			} else {
				// works if we're not comparing two abstimes... that is done in the comparator itself
				arrivalSortingIndex = 100000;
			}
		}
	}
}
