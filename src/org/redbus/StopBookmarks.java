package org.redbus;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

public class StopBookmarks extends ListActivity 
{	
	private static final String[] columnNames = new String[] { rEdBusDBHelper.BOOKMARKS_ID, rEdBusDBHelper.BOOKMARKS_STOPNAME };
	private static final int[] listViewIds = new int[] { R.id.stopbookmarks_stopcode, R.id.stopbookmarks_name };
	private Cursor listContentsCursor = null;
	private long BookmarkId = -1;

	@Override
	public void onCreate(Bundle savedInstanceState) 
	{
        super.onCreate(savedInstanceState);
        setContentView(R.layout.stopbookmarks);
        registerForContextMenu(getListView());
	}

	@Override
	protected void onStart() 
	{
		super.onStart();
		
		UpdateBookmarksList();
	}
	
	public void UpdateBookmarksList()
	{
		if (listContentsCursor != null) {
			stopManagingCursor(listContentsCursor);
			listContentsCursor.close();
			listContentsCursor = null;
		}

        rEdBusDBHelper db = new rEdBusDBHelper(this, false);
        try {
	        listContentsCursor = db.GetBookmarks();
	        startManagingCursor(listContentsCursor);
	        setListAdapter(new SimpleCursorAdapter(this, R.layout.stopbookmarks_item, listContentsCursor, columnNames, listViewIds));
        } finally {
        	db.close();
        }
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {		
		Intent i = new Intent(this, BusTimes.class);
		i.putExtra("StopCode", id);
		startActivity(i);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.stopbookmarks_item_menu, menu);	    
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		BookmarkId = ((AdapterView.AdapterContextMenuInfo) item.getMenuInfo()).id;
		
		switch(item.getItemId()) {
		case R.id.stopbookmarks_item_menu_bustimes:
			Intent i = new Intent(this, BusTimes.class);
			i.putExtra("StopCode", BookmarkId);
			startActivity(i);
			return true;
			
		case R.id.stopbookmarks_item_menu_showonmap:
			// FIXME
			return true;
			
		case R.id.stopbookmarks_item_menu_edit:
			// FIXME
			return true;

		case R.id.stopbookmarks_item_menu_delete:
			new AlertDialog.Builder(this).
				setMessage("Are you sure you want to delete this bookmark?").
				setNegativeButton("Cancel", null).
				setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        rEdBusDBHelper db = new rEdBusDBHelper(StopBookmarks.this, true);
                        try {
                        	db.DeleteBookmark(StopBookmarks.this.BookmarkId);
                        } finally {
                        	db.close();
                        }
                        StopBookmarks.this.UpdateBookmarksList();
                    }
				}).
                show();
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
		case R.id.stopbookmarks_menu_nearby_stops:
			// FIXME
			return true;
		case R.id.stopbookmarks_menu_bustimes:
			Intent i = new Intent(this, BusTimes.class);
			i.putExtra("StopCode", 36237382L); // HACK
			startActivity(i);
			return true;
		}
		
		return false;
	}
}
