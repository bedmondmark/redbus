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

package org.redbus;

import java.io.FileReader;
import java.io.FileWriter;
import java.util.Date;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
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

public class StopBookmarksActivity extends ListActivity implements BusStopDatabaseUpdateResponseListener
{	
	private static final String[] columnNames = new String[] { LocalDBHelper.ID, LocalDBHelper.BOOKMARKS_COL_STOPNAME };
	private static final int[] listViewIds = new int[] { R.id.stopbookmarks_stopcode, R.id.stopbookmarks_name };
	private static final String bookmarksXmlFile = "/sdcard/redbus-stops.xml";

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
        LocalDBHelper db = new LocalDBHelper(this);
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
        	
        	long nextUpdateCheck = Long.parseLong(db.getGlobalSetting("NEXTUPDATECHECK", "0"));
			long lastUpdateDate = Long.parseLong(db.getGlobalSetting("LASTUPDATE", "-1"));
        	
        	// do update check
        	if (nextUpdateCheck <= new Date().getTime()) {
        		isManualUpdateCheck = false;
        		BusStopDatabaseUpdateHelper.checkForUpdates(lastUpdateDate, this);
        		
            	// setup time for next check
            	nextUpdateCheck = new Date().getTime();
            	nextUpdateCheck += 24 * 60 * 60 * 1000; // 1 day
            	nextUpdateCheck += (long) (Math.random() * (12 * 60 * 60 * 1000.0)); // some random time in the 12 hours afterwards
            	db.setGlobalSetting("NEXTUPDATECHECK", Long.toString(nextUpdateCheck));
        	}
        	
        } catch (Throwable t) {
        	// ignore
        } finally {
        	db.close();
        }
	}

	@Override
	protected void onStart() 
	{
		super.onStart();
		
		update();
	}
	
	private void update()
	{
        LocalDBHelper db = new LocalDBHelper(this);
        try {
        	SimpleCursorAdapter oldAdapter = ((SimpleCursorAdapter) getListAdapter());
        	if (oldAdapter != null)
        		oldAdapter.getCursor().close();
	        Cursor listContentsCursor = db.getBookmarks();
	        startManagingCursor(listContentsCursor);
	        setListAdapter(new SimpleCursorAdapter(this, R.layout.stopbookmarks_item, listContentsCursor, columnNames, listViewIds));
        } finally {
        	db.close();
        }
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		BusTimesActivity.showActivity(this, id);
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
			BusTimesActivity.showActivity(this, bookmarkId);
			return true;

		case R.id.stopbookmarks_item_menu_showonmap:
			PointTree pt = PointTree.getPointTree(this);
			int stopNodeIdx = pt.lookupStopNodeIdxByStopCode((int) bookmarkId);
			if (stopNodeIdx != -1)
				StopMapActivity.showActivity(this, pt.lat[stopNodeIdx], pt.lon[stopNodeIdx]);
			return true;

		case R.id.stopbookmarks_item_menu_rename:
			final EditText input = new EditText(this);
			input.setText(bookmarkName);

			new AlertDialog.Builder(this)
					.setTitle("Rename bookmark")
					.setView(input)
					.setPositiveButton(android.R.string.ok,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int whichButton) {
			                        LocalDBHelper db = new LocalDBHelper(StopBookmarksActivity.this);
			                        try {
			                        	db.renameBookmark(StopBookmarksActivity.this.bookmarkId, input.getText().toString());
			                        } finally {
			                        	db.close();
			                        }
			                        StopBookmarksActivity.this.update();
								}
							})
					.setNegativeButton(android.R.string.cancel, null)
					.show();
			return true;

		case R.id.stopbookmarks_item_menu_delete:
			new AlertDialog.Builder(this)
				.setTitle("Delete bookmark")
				.setMessage("Are you sure you want to delete this bookmark?")
				.setPositiveButton(android.R.string.ok, 
						new DialogInterface.OnClickListener() {
		                    public void onClick(DialogInterface dialog, int whichButton) {
		                        LocalDBHelper db = new LocalDBHelper(StopBookmarksActivity.this);
		                        try {
		                        	db.deleteBookmark(StopBookmarksActivity.this.bookmarkId);
		                        } finally {
		                        	db.close();
		                        }
		                        StopBookmarksActivity.this.update();
		                    }
						})
				.setNegativeButton(android.R.string.cancel, null)
                .show();
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
		final EditText input = new EditText(this);
		input.setInputType(InputType.TYPE_CLASS_PHONE);
		input.setFilters(new InputFilter[] { new InputFilter.LengthFilter(8), new DigitsKeyListener() } );

		switch(item.getItemId()) {
		case R.id.stopbookmarks_menu_nearby_stops:
        	StopMapActivity.showActivity(StopBookmarksActivity.this);
			return true;

		case R.id.stopbookmarks_menu_bustimes:
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
								PointTree pt = PointTree.getPointTree(StopBookmarksActivity.this);
								int stopNodeIdx = pt.lookupStopNodeIdxByStopCode((int) stopCode);
								if (stopNodeIdx != -1) {
									BusTimesActivity.showActivity(StopBookmarksActivity.this, (int) stopCode);
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
			return true;				
			
		case R.id.stopbookmarks_menu_backup: {
	        LocalDBHelper db = new LocalDBHelper(this);
	        Cursor c = null;
	        FileWriter output = null;
	        try {
	        	output = new FileWriter(bookmarksXmlFile);
	            XmlSerializer serializer = Xml.newSerializer();
                serializer.setOutput(output);
                serializer.startDocument("UTF-8", true);
                serializer.startTag("", "redbus");

		        c = db.getBookmarks();
		        while(c.moveToNext()) {
                    serializer.startTag("", "busstop");
                    serializer.attribute("", "stopcode", Long.toString(c.getLong(0)));
                    serializer.attribute("", "name", c.getString(1));
                    serializer.endTag("", "busstop");
		        }
                serializer.endTag("", "redbus");
                serializer.endDocument();
	        } catch (Throwable t) {
	        	Log.e("StopBookmarks.Backup", "Backup failed", t);
	        	return true;
	        } finally {
	        	if (output != null) {
	        		try {
		        		output.flush();
	        		} catch (Throwable t) {}
	        		try {
		        		output.close();
	        		} catch (Throwable t) {}
	        	}
	        	if (c != null)
	        		c.close();
	        	db.close();
	        }
	        Toast.makeText(this, "Bookmarks saved to " + bookmarksXmlFile, Toast.LENGTH_SHORT).show();
			return true;
		}
			
		case R.id.stopbookmarks_menu_restore: {
	        LocalDBHelper db = new LocalDBHelper(this);
	        FileReader inputFile = null;
	        try {
	        	inputFile = new FileReader(bookmarksXmlFile);
	        	
				XmlPullParser parser = Xml.newPullParser();
				parser.setInput(inputFile);
				db.deleteBookmarks();

				while(parser.next() != XmlPullParser.END_DOCUMENT) {
					switch(parser.getEventType()) {
					case XmlPullParser.START_TAG:
						String tagName = parser.getName();
						if (tagName == "busstop") {
							long stopCode = Long.parseLong(parser.getAttributeValue(null, "stopcode"));
							String stopName = parser.getAttributeValue(null, "name");
							db.addBookmark(stopCode, stopName);
						}
					}
				}
	        } catch (Throwable t) {
	        	Log.e("StopBookmarks.Restore", "Restore failed", t);
	        	return true;
	        } finally {
	        	if (inputFile != null) {
	        		try {
	        			inputFile.close();
	        		} catch (Throwable t) {}
	        	}
	        	db.close();
	        }
	        
	        Toast.makeText(this, "Bookmarks restored from " + bookmarksXmlFile, Toast.LENGTH_SHORT).show();
	        update();
			return true;		
		}
		
		case R.id.stopbookmarks_menu_checkupdates: {
	        // display changes popup
	        LocalDBHelper db = new LocalDBHelper(this);
			long lastUpdateDate = -1;
	        try {
				lastUpdateDate = Long.parseLong(db.getGlobalSetting("LASTUPDATE", "-1"));
	        } catch (Exception e) {
	        	return true;
	        } finally {
	        	db.close();
	        }
	        
    		isManualUpdateCheck = true;
    		BusStopDatabaseUpdateHelper.checkForUpdates(lastUpdateDate, this);
    		return true;
		}
		}

		return false;
	}

	public void checkUpdatesError() {
		Toast.makeText(this, "Failed to check for bus stop data updates; please try again later", Toast.LENGTH_SHORT).show();
	}

	public void checkUpdatesSuccess(long updateDate) {
		if (updateDate == 0) {
			if (isManualUpdateCheck)
				Toast.makeText(this, "No new bus stop data available", Toast.LENGTH_SHORT).show();
			return;
		}
		
		final long updateDateF = updateDate;
		new AlertDialog.Builder(this)
			.setTitle("New bus stops")
			.setMessage("New bus stop data is available; shall I download it now?")
			.setPositiveButton(android.R.string.ok, 
					new DialogInterface.OnClickListener() {
	                    public void onClick(DialogInterface dialog, int whichButton) {
	                    	BusStopDatabaseUpdateHelper.getUpdate(updateDateF, StopBookmarksActivity.this);
	                    }
					})
			.setNegativeButton(android.R.string.cancel, null)
	        .show();		
	}

	public void getUpdateError() {
		Toast.makeText(this, "Failed to download update; please try again later", Toast.LENGTH_SHORT).show();
	}

	public void getUpdateSuccess(long updateDate, byte[] updateData) {
		
		try {
			PointTree.saveNewDatabase(updateData);
		} catch (Throwable t) {
			Log.e("BusStopDatabaseUpdateHelper", "onPostExecute", t);
			Toast.makeText(this, "Failed to download update; please try again later", Toast.LENGTH_SHORT).show();
			return;
		}

		Toast.makeText(this, "Update downloaded, and installed successfully...", Toast.LENGTH_SHORT).show();
		
        LocalDBHelper db = new LocalDBHelper(this);
        try {
        	db.setGlobalSetting("LASTUPDATE", Long.toString(updateDate));
        } catch (Exception e) {
        	// ignore
        } finally {
        	db.close();
        }
	}
}
