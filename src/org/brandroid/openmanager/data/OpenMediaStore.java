package org.brandroid.openmanager.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.util.Hashtable;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;

public class OpenMediaStore extends OpenPath
{
	private OpenCursor curs;
	private String id;
	private String name;
	private OpenFile mFile = null;
	private long size = -1;
	private long modified = -1;
	
	public OpenMediaStore(OpenCursor parent)
	{
		curs = parent;
		Cursor cursor = parent.getCursor();
		id = cursor.getString(0);
		if(cursor.getColumnCount() > 1)
			name = cursor.getString(1);
		if(cursor.getColumnCount() > 2)
			mFile = new OpenFile(cursor.getString(2));
		try {
			size = cursor.getLong(cursor.getColumnIndexOrThrow("_size"));
		} catch(Exception e) {
			if(mFile != null)
				size = mFile.length();
		}
		try {
			modified = cursor.getLong(cursor.getColumnIndexOrThrow("date_modified"));
		} catch(Exception e) {
			if(mFile != null)
				modified = mFile.lastModified();
		}
				
	}

	@Override
	public String getName() {
		return name;
	}
	
	public OpenFile getFile() { return mFile; }

	@Override
	public String getPath() {
		return mFile == null ? getParent().getName() + id : mFile.getPath();
	}

	@Override
	public String getAbsolutePath() {
		return mFile == null ? null : mFile.getAbsolutePath();
	}

	@Override
	public long length() {
		return size;
	}

	@Override
	public OpenCursor getParent() {
		return curs;
	}

	@Override
	public OpenPath getChild(String name) {
		return null;
	}

	@Override
	public OpenPath[] list() {
		return null;
	}

	@Override
	public OpenPath[] listFiles() {
		return null;
	}

	@Override
	public Boolean isDirectory() {
		return false;
	}

	@Override
	public Boolean isFile() {
		return true;
	}

	@Override
	public Boolean isHidden() {
		return mFile == null ? false : mFile.isHidden();
	}

	@Override
	public Uri getUri() {
		return mFile == null ? null : mFile.getUri();
	}

	@Override
	public Long lastModified() {
		return mFile == null ? modified : mFile.lastModified();
	}

	@Override
	public Boolean canRead() {
		return mFile == null ? true : mFile.canRead();
	}

	@Override
	public Boolean canWrite() {
		return mFile == null ? false : mFile.canWrite();
	}

	@Override
	public Boolean canExecute() {
		return mFile == null ? true : mFile.canExecute();
	}

	@Override
	public Boolean exists() {
		return true;
	}

	@Override
	public Boolean requiresThread() {
		return false;
	}

	@Override
	public Boolean delete() {
		return false;
	}

	@Override
	public Boolean mkdir() {
		return false;
	}

	@Override
	public InputStream getInputStream() throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public OutputStream getOutputStream() throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	public long getMediaID() {
		return Long.parseLong(id);
	}

}