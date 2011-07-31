/*
 * Copyright 2010 Andrew De Quincey -  adq@lidskialf.net
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

package org.redbus.ui;

import java.io.FileReader;
import java.io.FileWriter;
import java.util.Date;

import org.redbus.R;
import org.redbus.R.drawable;
import org.redbus.R.id;
import org.redbus.R.layout;
import org.redbus.R.menu;
import org.redbus.R.string;
import org.redbus.settings.SettingsAccessor;
import org.redbus.stopdb.IStopDbUpdateResponseListener;
import org.redbus.stopdb.StopDbAccessor;
import org.redbus.stopdb.StopDbUpdater;
import org.redbus.ui.alert.AlertUtils;
import org.redbus.ui.arrivaltime.ArrivalTimeActivity;
import org.redbus.ui.stopmap.StopMapActivity;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;


import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import android.util.Xml;

public class StopBookmarksActivity extends ListActivity implements IStopDbUpdateResponseListener, OnCancelListener
{	
	public static final String[] columnNames = new String[] { SettingsAccessor.ID, SettingsAccessor.BOOKMARKS_COL_STOPNAME };
	public static final int[] listViewIds = new int[] { R.id.stopbookmarks_stopcode, R.id.stopbookmarks_name };
	private static final String bookmarksXmlFile = "/sdcard/redbus-stops.xml";
	
	private ProgressDialog busyDialog = null;
	private int expectedRequestId = -1;

	private long bookmarkId = -1;
	private String bookmarkName = null;
	private boolean isManualUpdateCheck = false;

	@Override
	public void onCreate(Bundle savedInstanceState) 
	{
        super.onCreate(savedInstanceState);
        setTitle("Bookmarks");
        setContentView(R.layout.stopbookmarks);
        registerForContextMenu(getListView());
        
        // display changes popup
        SettingsAccessor db = new SettingsAccessor(this);
        try {
        	PackageInfo pi = getPackageManager().getPackageInfo("org.redbus", 0);
        	if (!db.getGlobalSetting("PREVIOUSVERSIONCODE", "").equals(Integer.toString(pi.versionCode))) {
        		new AlertDialog.Builder(this).
        			setIcon(null).
        			setTitle("v" + pi.versionName + " changes").
	    			setMessage(R.string.newversiontext).
	    			setPositiveButton(android.R.string.ok, null).
	    			show();        	
        		db.setGlobalSetting("PREVIOUSVERSIONCODE", Integer.toString(pi.versionCode));
        	}
        	
        	// get update details
        	long nextUpdateCheck = Long.parseLong(db.getGlobalSetting("NEXTUPDATECHECK", "0"));
			long lastUpdateDate = Long.parseLong(db.getGlobalSetting("LASTUPDATE", "-1"));
        	
            // are we being called from a click on a notification?
            if (getIntent().getBooleanExtra("DoManualUpdate", false)) {
            	busyDialog = BusyDialog.show(StopBookmarksActivity.this, StopBookmarksActivity.this, busyDialog, "Checking for updates...");
        		isManualUpdateCheck = true;
        		expectedRequestId = StopDbUpdater.checkForUpdates(lastUpdateDate, this);
            } else {
            	// otherwise, we do an background update check
	        	if (nextUpdateCheck <= new Date().getTime() / 1000) {
	        		isManualUpdateCheck = false;
	        		expectedRequestId = StopDbUpdater.checkForUpdates(lastUpdateDate, this);
	        	}
            }
        } catch (Throwable t) {
        	// ignore
        } finally {
        	db.close();
        }
	}

	@Override
	protected void onResume() 
	{
		super.onResume();
		
		update();
	}
	
	public void onCancel(DialogInterface dialog) {
		expectedRequestId = -1;
	}
	
	private void update()
	{
        SettingsAccessor db = new SettingsAccessor(this);
        try {
        	SimpleCursorAdapter oldAdapter = ((SimpleCursorAdapter) getListAdapter());
        	if (oldAdapter != null) {
        		stopManagingCursor(oldAdapter.getCursor());
        		oldAdapter.getCursor().close();
        	}
	        Cursor listContentsCursor = db.getBookmarks();
	        startManagingCursor(listContentsCursor);
	        setListAdapter(new SimpleCursorAdapter(this, R.layout.stopbookmarks_item, listContentsCursor, columnNames, listViewIds));
        } finally {
        	db.close();
        }
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		ArrivalTimeActivity.showActivity(this, (int) id);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.stopbookmarks_item_menu, menu);	    
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterView.AdapterContextMenuInfo menuInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
		bookmarkId = menuInfo.id;
		bookmarkName = ((TextView) menuInfo.targetView.findViewById(R.id.stopbookmarks_name)).getText().toString();

		switch(item.getItemId()) {
		case R.id.stopbookmarks_item_menu_bustimes:
			doShowArrivalTimes((int) bookmarkId);
			return true;

		case R.id.stopbookmarks_item_menu_showonmap:
			doShowOnMap((int) bookmarkId);
			return true;

		case R.id.stopbookmarks_item_menu_rename:
			doRenameBookmark((int) bookmarkId);
			return true;

		case R.id.stopbookmarks_item_menu_delete:
			doDeleteBookmark((int) bookmarkId);
			return true;	
		}

		return super.onContextItemSelected(item);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {		
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.stopbookmarks_menu, menu);
	    return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case R.id.stopbookmarks_menu_bustimes:
			doShowBusTimes();
			return true;				
			
		case R.id.stopbookmarks_menu_backup:
			doBookmarksBackup();
			return true;
			
		case R.id.stopbookmarks_menu_restore:
			doBookmarksRestore();
			return true;
		
		case R.id.stopbookmarks_menu_checkupdates:
			doCheckStopDbUpdate();
			return true;
		}

		return false;
	}
	
	private void doShowArrivalTimes(int stopCode) {
		ArrivalTimeActivity.showActivity(this, stopCode);		
	}
	
	private void doShowOnMap(int stopCode) {
		StopDbAccessor pt = StopDbAccessor.Load(this);
		int stopNodeIdx = pt.lookupStopNodeIdxByStopCode(stopCode);
		if (stopNodeIdx != -1)
			StopMapActivity.showActivity(this, pt.lat[stopNodeIdx], pt.lon[stopNodeIdx]);
	}
	
	private void doRenameBookmark(int stopCode) {
		final int localStopCode = stopCode;
		final EditText input = new EditText(this);
		input.setText(bookmarkName);

		new AlertDialog.Builder(this)
				.setTitle("Rename bookmark")
				.setView(input)
				.setPositiveButton(android.R.string.ok,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int whichButton) {
		                        SettingsAccessor db = new SettingsAccessor(StopBookmarksActivity.this);
		                        try {
		                        	db.renameBookmark(localStopCode, input.getText().toString());
		                        } finally {
		                        	db.close();
		                        }
		                        StopBookmarksActivity.this.update();
							}
						})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}
	
	private void doDeleteBookmark(int stopCode) {
		final int localStopCode = stopCode;

		new AlertDialog.Builder(this)
		.setTitle("Delete bookmark")
		.setMessage("Are you sure you want to delete this bookmark?")
		.setPositiveButton(android.R.string.ok, 
				new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        SettingsAccessor db = new SettingsAccessor(StopBookmarksActivity.this);
                        try {
                        	db.deleteBookmark(localStopCode);
                        } finally {
                        	db.close();
                        }
                        StopBookmarksActivity.this.update();
                    }
				})
		.setNegativeButton(android.R.string.cancel, null)
        .show();
	}
	
	private void doShowBusTimes() {
		final EditText input = new EditText(this);
		input.setInputType(InputType.TYPE_CLASS_PHONE);
		input.setFilters(new InputFilter[] { new InputFilter.LengthFilter(8), new DigitsKeyListener() } );

		new AlertDialog.Builder(this)
		.setTitle("Enter stopcode")
		.setView(input)
		.setPositiveButton(android.R.string.ok,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						long stopCode = -1;
						try {
							stopCode = Long.parseLong(input.getText().toString());
						} catch (Exception ex) {
							new AlertDialog.Builder(StopBookmarksActivity.this)
									.setTitle("Error")
									.setMessage("The stopcode was invalid; please try again using only numbers")
									.setPositiveButton(android.R.string.ok, null)
									.show();
							return;
						}
						StopDbAccessor pt = StopDbAccessor.Load(StopBookmarksActivity.this);
						int stopNodeIdx = pt.lookupStopNodeIdxByStopCode((int) stopCode);
						if (stopNodeIdx != -1) {
							ArrivalTimeActivity.showActivity(StopBookmarksActivity.this, (int) stopCode);
						} else {
							new AlertDialog.Builder(StopBookmarksActivity.this)
								.setTitle("Error")
								.setMessage("The stopcode was invalid; please try again")
								.setPositiveButton(android.R.string.ok, null)
								.show();
						}
					}
				})
		.setNegativeButton(android.R.string.cancel, null)
		.show();
	}
	
	private void doBookmarksBackup() {
        SettingsAccessor db = new SettingsAccessor(this);
        if (db.backup(bookmarksXmlFile))
        	Toast.makeText(this, "Bookmarks saved to " + bookmarksXmlFile, Toast.LENGTH_SHORT).show();
	}
	
	private void doBookmarksRestore() {
        SettingsAccessor db = new SettingsAccessor(this);
        if (db.restore(bookmarksXmlFile)) {
	        Toast.makeText(this, "Bookmarks restored from " + bookmarksXmlFile, Toast.LENGTH_SHORT).show();
	        update();
        }
	}
	
	private void doCheckStopDbUpdate() {
        // display changes popup
        SettingsAccessor db = new SettingsAccessor(this);
		long lastUpdateDate = -1;
        try {
			lastUpdateDate = Long.parseLong(db.getGlobalSetting("LASTUPDATE", "-1"));
        } catch (Exception e) {
        	return;
        } finally {
        	db.close();
        }
        
		busyDialog = BusyDialog.show(this, this, busyDialog, "Checking for updates...");
		isManualUpdateCheck = true;
		expectedRequestId = StopDbUpdater.checkForUpdates(lastUpdateDate, this);

	}
	
	
	
	
	
	
	
	
	
	

	private void setNextUpdateTime(boolean wasSuccessful) {	
        SettingsAccessor db = new SettingsAccessor(this);
        try {
        	int retryCount = Integer.parseInt(db.getGlobalSetting("UPDATECHECKRETRIES", "0"));
        	if ((retryCount < 3) && (!wasSuccessful)) {
        		// if it was unsuccessful and we've had less that 3 retries, try again in a minute
    	    	long nextUpdateCheck = new Date().getTime() / 1000;
    	    	nextUpdateCheck += 60; // 1 min
    	    	db.setGlobalSetting("NEXTUPDATECHECK", Long.toString(nextUpdateCheck));
    	    	db.getGlobalSetting("UPDATECHECKRETRIES", Integer.toString(retryCount + 1));
        	} else {
        		// if it was successful OR we've had > 2 retries, then we delay for ~24 hours before trying again
		    	long nextUpdateCheck = new Date().getTime() / 1000;
		    	nextUpdateCheck += 23 * 60 * 60; // 1 day
		    	nextUpdateCheck += (long) (Math.random() * (2 * 60 * 60.0)); // some random time in the 2 hours afterwards
		    	db.setGlobalSetting("NEXTUPDATECHECK", Long.toString(nextUpdateCheck));
		    	db.getGlobalSetting("UPDATECHECKRETRIES", "0");
        	}
        } finally {
        	db.close();
        }
	}

	public void checkUpdateError(int requestId) {
		if (requestId != expectedRequestId)
			return;

		BusyDialog.dismiss(busyDialog);
		
		if (isManualUpdateCheck)
			Toast.makeText(this, "Failed to check for bus stop data updates; please try again later", Toast.LENGTH_SHORT).show();
		setNextUpdateTime(false);
	}

	public void checkUpdateSuccess(int requestId, long updateDate) {
		if (requestId != expectedRequestId)
			return;

		BusyDialog.dismiss(busyDialog);
		setNextUpdateTime(true);

		if (updateDate == 0) {
			if (isManualUpdateCheck)
				Toast.makeText(this, "No new bus stop data available", Toast.LENGTH_SHORT).show();
			return;
		}

		// if its an automatic update check, we use a notification so we don't spam the user with unexpected popups.
		// if its a manual check, we /do/ show a popup
		if (!isManualUpdateCheck) {
			Intent i = new Intent(this, StopBookmarksActivity.class);
			i.putExtra("DoManualUpdate", true);
			PendingIntent contentIntent = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_CANCEL_CURRENT);

			Notification notification = new Notification(R.drawable.tracker_24x24_masked, "New bus stop data available", System.currentTimeMillis());
			notification.defaults  = 0;
			notification.flags |= Notification.FLAG_AUTO_CANCEL;
			notification.setLatestEventInfo(this, "New bus stop data available", "Press to download", contentIntent);

			NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			nm.notify(AlertUtils.ALERT_NOTIFICATION_ID, notification);
		} else {
			final long updateDateF = updateDate;
			new AlertDialog.Builder(this)
				.setTitle("New bus stops")
				.setMessage("New bus stop data is available; shall I download it now?")
				.setPositiveButton(android.R.string.ok, 
						new DialogInterface.OnClickListener() {
		                    public void onClick(DialogInterface dialog, int whichButton) {
		                    	busyDialog = BusyDialog.show(StopBookmarksActivity.this, StopBookmarksActivity.this, busyDialog, "Downloading bus data update...");
		                    	expectedRequestId = StopDbUpdater.getUpdate(updateDateF, StopBookmarksActivity.this);
		                    }
						})
				.setNegativeButton(android.R.string.cancel, null)
		        .show();		
		}
	}

	public void getUpdateError(int requestId) {
		if (requestId != expectedRequestId)
			return;

		BusyDialog.dismiss(busyDialog);

		Toast.makeText(this, "Failed to download update; please try again later", Toast.LENGTH_SHORT).show();
	}

	public void getUpdateSuccess(int requestId, long updateDate, byte[] updateData) {
		if (requestId != expectedRequestId)
			return;

		BusyDialog.dismiss(busyDialog);

		try {
			StopDbAccessor.saveRawDb(updateData);
		} catch (Throwable t) {
			Log.e("BusStopDatabaseUpdateHelper", "onPostExecute", t);
			Toast.makeText(this, "Failed to download update; please try again later", Toast.LENGTH_SHORT).show();
			return;
		}

		Toast.makeText(this, "Update downloaded, and installed successfully...", Toast.LENGTH_SHORT).show();
		
        SettingsAccessor db = new SettingsAccessor(this);
        try {
        	db.setGlobalSetting("LASTUPDATE", Long.toString(updateDate));
        } catch (Exception e) {
        	// ignore
        } finally {
        	db.close();
        }
	}
}
