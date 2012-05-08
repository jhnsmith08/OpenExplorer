/*
    Open Explorer, an open source file explorer & text editor
    Copyright (C) 2011 Brandon Bowles <brandroid64@gmail.com>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.brandroid.openmanager.util;

import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Build;
import android.provider.MediaStore.Images;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.view.View;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.Context;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RemoteViews;
import android.widget.Toast;
import android.widget.TextView;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.brandroid.openmanager.R;
import org.brandroid.openmanager.activities.BluetoothActivity;
import org.brandroid.openmanager.activities.OpenExplorer;
import org.brandroid.openmanager.activities.OperationsActivity;
import org.brandroid.openmanager.data.OpenCursor;
import org.brandroid.openmanager.data.OpenPath;
import org.brandroid.openmanager.data.OpenFile;
import org.brandroid.openmanager.data.OpenSMB;
import org.brandroid.openmanager.data.OpenSmartFolder;
import org.brandroid.openmanager.fragments.DialogHandler;
import org.brandroid.openmanager.util.FileManager.OnProgressUpdateCallback;
import org.brandroid.utils.Logger;
import org.brandroid.utils.MenuUtils;
import org.brandroid.utils.Utils;

public class EventHandler {
	public static final int SEARCH_TYPE = 0x00;
	public static final int COPY_TYPE = 0x01;
	public static final int UNZIP_TYPE = 0x02;
	public static final int UNZIPTO_TYPE = 0x03;
	public static final int ZIP_TYPE = 0x04;
	public static final int DELETE_TYPE = 0x05;
	public static final int RENAME_TYPE = 0X06;
	public static final int MKDIR_TYPE = 0x07;
	public static final int CUT_TYPE = 0x08;
	public static final int ERROR_TYPE = 0x09;
	public static final int BACKGROUND_NOTIFICATION_ID = 123;
	
	public static boolean SHOW_NOTIFICATION_STATUS = !OpenExplorer.IS_BLACKBERRY && Build.VERSION.SDK_INT > 10;

	private static NotificationManager mNotifier = null;
	private static int EventCount = 0;

	private OnWorkerUpdateListener mThreadListener;
	private FileManager mFileMang;

	private static ArrayList<BackgroundWork> mTasks = new ArrayList<BackgroundWork>();

	public static ArrayList<BackgroundWork> getTaskList() {
		return mTasks;
	}

	public static BackgroundWork[] getRunningTasks() {
		ArrayList<BackgroundWork> ret = new ArrayList<EventHandler.BackgroundWork>();
		for (BackgroundWork bw : mTasks)
			if (bw.getStatus() != Status.FINISHED)
				ret.add(bw);
		return ret.toArray(new BackgroundWork[0]);
	}

	public static boolean hasRunningTasks() {
		for (BackgroundWork bw : mTasks)
			if (bw.getStatus() == Status.RUNNING)
				return true;
		return false;
	}

	public static void cancelRunningTasks() {
		for (BackgroundWork bw : mTasks)
			if (bw.getStatus() == Status.RUNNING)
				bw.cancel(true);
		if (mNotifier != null)
			mNotifier.cancel(BACKGROUND_NOTIFICATION_ID);
	}

	public interface OnWorkerUpdateListener {
		public void onWorkerThreadComplete(int type, ArrayList<String> results);

		public void onWorkerProgressUpdate(int pos, int total);
	}

	private synchronized void OnWorkerProgressUpdate(int pos, int total) {
		if (mThreadListener == null)
			return;
		mThreadListener.onWorkerProgressUpdate(pos, total);
	}

	private synchronized void OnWorkerThreadComplete(int type,
			ArrayList<String> results) {
		if (mThreadListener == null)
			return;
		mThreadListener.onWorkerThreadComplete(type, results);
		mThreadListener = null;
	}

	public void setUpdateListener(OnWorkerUpdateListener e) {
		mThreadListener = e;
	}

	public EventHandler(FileManager filemanager) {
		mFileMang = filemanager;
	}

	public static String getResourceString(Context mContext, int... resIds) {
		String ret = "";
		for (int resId : resIds)
			ret += (ret == "" ? "" : " ") + mContext.getText(resId);
		return ret;
	}

	public void deleteFile(final OpenPath file, final Context mContext,
			boolean showConfirmation) {
		List<OpenPath> files = new ArrayList<OpenPath>();
		files.add(file);
		deleteFile(files, mContext, showConfirmation);
	}

	public void deleteFile(final List<OpenPath> path, final Context mContext,
			boolean showConfirmation) {
		final OpenPath[] files = new OpenPath[path.size()];
		path.toArray(files);
		String name;

		if (path.size() == 1)
			name = path.get(0).getName();
		else
			name = path.size() + " "
					+ getResourceString(mContext, R.string.s_files);

		if (!showConfirmation) {
			new BackgroundWork(DELETE_TYPE, mContext, null).execute(files);
			return;
		}
		AlertDialog.Builder b = new AlertDialog.Builder(mContext);
		b.setTitle(
				getResourceString(mContext, R.string.s_menu_delete) + " "
						+ name)
				.setMessage(
						mContext.getString(R.string.s_alert_confirm_delete,
								name))
				.setIcon(R.drawable.ic_menu_delete)
				.setPositiveButton(
						getResourceString(mContext, R.string.s_menu_delete),
						new OnClickListener() {
							public void onClick(DialogInterface dialog,
									int which) {
								new BackgroundWork(DELETE_TYPE, mContext, null)
										.execute(files);
							}
						})
				.setNegativeButton(
						getResourceString(mContext, R.string.s_cancel),
						new OnClickListener() {
							public void onClick(DialogInterface dialog,
									int which) {
								dialog.dismiss();
							}
						}).create().show();
	}

	public void startSearch(final OpenPath base, final Context mContext) {
		final InputDialog dSearch = new InputDialog(mContext)
				.setIcon(R.drawable.search).setTitle(R.string.s_search)
				.setCancelable(true)
				.setMessageTop(R.string.s_prompt_search_within)
				.setDefaultTop(base.getPath())
				.setMessage(R.string.s_prompt_search);
		AlertDialog alert = dSearch
				.setPositiveButton(R.string.s_search, new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						searchFile(new OpenFile(dSearch.getInputTopText()),
								dSearch.getInputText(), mContext);
					}
				}).setNegativeButton(R.string.s_cancel, new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				}).create();
		alert.setInverseBackgroundForced(true);
		alert.show();
	}

	public void renameFile(final OpenPath path, boolean isFolder,
			Context mContext) {
		final InputDialog dRename = new InputDialog(mContext)
				.setIcon(R.drawable.ic_rename).setTitle(R.string.s_menu_rename)
				.setCancelable(true).setMessage(R.string.s_alert_rename)
				.setDefaultText(path.getName());
		dRename.setPositiveButton(android.R.string.ok,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						if (dRename.getInputText().toString().length() > 0) {
							if (mFileMang.renameTarget(path.getPath(), dRename
									.getInputText().toString()))
								OnWorkerThreadComplete(RENAME_TYPE, null);
						} else
							dialog.dismiss();
					}
				})
				.setNegativeButton(android.R.string.cancel,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int which) {
								dialog.dismiss();
							}
						}).create().show();
	}

	/**
	 * 
	 * @param directory
	 *            directory path to create the new folder in.
	 */
	public void createNewFolder(final String directory, Context mContext) {
		final InputDialog dlg = new InputDialog(mContext)
				.setTitle(R.string.s_title_newfolder)
				.setIcon(R.drawable.ic_menu_folder_add_dark)
				.setMessage(R.string.s_alert_newfolder)
				.setMessageTop(R.string.s_alert_newfolder_folder)
				.setDefaultTop(directory, false).setCancelable(true)
				.setNegativeButton(R.string.s_cancel, new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				});
		dlg.setPositiveButton(R.string.s_create, new OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				String name = dlg.getInputText();
				if (name.length() > 0) {
					if (mFileMang != null)
						mFileMang.createDir(directory, name);
					OnWorkerThreadComplete(MKDIR_TYPE, null);
				} else {
					dialog.dismiss();
				}
			}
		});
		dlg.create().show();
	}

	public void sendFile(final List<OpenPath> path, final Context mContext) {
		String name;
		CharSequence[] list = { "Bluetooth", "Email" };
		final OpenPath[] files = new OpenPath[path.size()];
		path.toArray(files);
		final int num = path.size();

		if (num == 1)
			name = path.get(0).getName();
		else
			name = path.size() + " "
					+ getResourceString(mContext, R.string.s_files) + ".";

		AlertDialog.Builder b = new AlertDialog.Builder(mContext);
		b.setTitle(
				getResourceString(mContext, R.string.s_title_send).toString()
						.replace("xxx", name)).setIcon(R.drawable.bluetooth)
				.setItems(list, new OnClickListener() {

					public void onClick(DialogInterface dialog, int which) {
						switch (which) {
						case 0:
							Intent bt = new Intent(mContext,
									BluetoothActivity.class);

							bt.putExtra("paths", files);
							mContext.startActivity(bt);
							break;

						case 1:
							ArrayList<Uri> uris = new ArrayList<Uri>();
							Intent mail = new Intent();
							mail.setType("application/mail");

							if (num == 1) {
								mail.setAction(android.content.Intent.ACTION_SEND);
								mail.putExtra(Intent.EXTRA_STREAM,
										files[0].getUri());
								mContext.startActivity(mail);
								break;
							}

							for (int i = 0; i < num; i++)
								uris.add(files[i].getUri());

							mail.setAction(android.content.Intent.ACTION_SEND_MULTIPLE);
							mail.putParcelableArrayListExtra(
									Intent.EXTRA_STREAM, uris);
							mContext.startActivity(mail);
							break;
						}
					}
				}).create().show();
	}

	public void copyFile(OpenPath source, OpenPath destPath, Context mContext) {
		if (!destPath.isDirectory())
			destPath = destPath.getParent();
		new BackgroundWork(COPY_TYPE, mContext, destPath, source.getName())
				.execute(source);
	}

	public void copyFile(List<OpenPath> files, OpenPath newPath,
			Context mContext) {
		OpenPath[] array = new OpenPath[files.size()];
		files.toArray(array);

		String title = array.length + " "
				+ mContext.getString(R.string.s_files);
		if (array.length == 1)
			title = array[0].getPath();
		new BackgroundWork(COPY_TYPE, mContext, newPath, title).execute(array);
	}

	public void cutFile(List<OpenPath> files, OpenPath newPath, Context mContext) {
		OpenPath[] array = new OpenPath[files.size()];
		files.toArray(array);

		new BackgroundWork(CUT_TYPE, mContext, newPath).execute(array);
	}

	public void searchFile(OpenPath dir, String query, Context mContext) {
		new BackgroundWork(SEARCH_TYPE, mContext, dir, query).execute();
	}

	public BackgroundWork zipFile(OpenPath into, List<OpenPath> files,
			Context mContext) {
		return zipFile(into, files.toArray(new OpenPath[0]), mContext);
	}

	public BackgroundWork zipFile(OpenPath into, OpenPath[] files,
			Context mContext) {
		BackgroundWork bw = new BackgroundWork(ZIP_TYPE, mContext, into);
		bw.execute(files);
		return bw;
	}

	public void unzipFile(final OpenPath file, final Context mContext) {
		final OpenPath into = file.getParent().getChild(
				file.getName().replace("." + file.getExtension(), ""));
		// AlertDialog.Builder b = new AlertDialog.Builder(mContext);
		final InputDialog dUnz = new InputDialog(mContext);
		dUnz.setTitle(
				getResourceString(mContext, R.string.s_title_unzip).toString()
						.replace("xxx", file.getName()))
				.setMessage(
						getResourceString(mContext, R.string.s_prompt_unzip)
								.toString().replace("xxx", file.getName()))
				.setIcon(R.drawable.lg_zip)
				.setPositiveButton(android.R.string.ok, new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						OpenPath path = new OpenFile(dUnz.getInputText());
						if (!path.exists() && !path.mkdir()) {
							Logger.LogError("Couldn't locate output path for unzip! "
									+ path.getPath());
							OnWorkerThreadComplete(ERROR_TYPE, null);
							return;
						}
						Logger.LogVerbose("Unzipping " + file.getPath()
								+ " into " + path);
						new BackgroundWork(UNZIP_TYPE, mContext, path)
								.execute(file);
					}
				}).setNegativeButton(android.R.string.cancel, null)
				.setDefaultText(into.getPath()).create().show();
	}

	public void unZipFileTo(OpenPath zipFile, OpenPath toDir, Context mContext) {
		new BackgroundWork(UNZIPTO_TYPE, mContext, toDir).execute(zipFile);
	}

	/**
	 * Do work on second thread class
	 * 
	 * @author Joe Berria
	 */
	public class BackgroundWork extends AsyncTask<OpenPath, Integer, Integer>
			implements OnProgressUpdateCallback {
		private final int mType;
		private final Context mContext;
		private final String[] mInitParams;
		private final OpenPath mIntoPath;
		private ProgressDialog mPDialog;
		private Notification mNote = null;
		private final int mNotifyId;
		private ArrayList<String> mSearchResults = null;
		private boolean isDownload = false;
		private int taskId = -1;
		private final Date mStart;
		private long mLastRate = 0;
		private long mRemain = 0l;
		private boolean notifReady = false;
		private final int[] mLastProgress = new int[3];
		private int notifIcon;

		private OnWorkerUpdateListener mListener;

		public void setWorkerUpdateListener(OnWorkerUpdateListener listener) {
			mListener = listener;
		}

		public void OnWorkerThreadComplete(int type, ArrayList<String> results) {
			if (mListener != null)
				mListener.onWorkerThreadComplete(type, results);
			EventHandler.this.OnWorkerThreadComplete(type, results);
		}

		public void OnWorkerProgressUpdate(int pos, int total) {
			if (mListener != null)
				mListener.onWorkerProgressUpdate(pos, total);
			EventHandler.this.OnWorkerProgressUpdate(pos, total);
		}

		public BackgroundWork(int type, Context context, OpenPath intoPath,
				String... params) {
			mType = type;
			mContext = context;
			mInitParams = params;
			mIntoPath = intoPath;
			if (mNotifier == null)
				mNotifier = (NotificationManager) context
						.getSystemService(Context.NOTIFICATION_SERVICE);
			taskId = mTasks.size();
			mTasks.add(this);
			mStart = new Date();
			mNotifyId = BACKGROUND_NOTIFICATION_ID + EventCount++;
		}

		public String getTitle() {
			String title = getResourceString(mContext,
					R.string.s_title_executing).toString();
			switch (mType) {
			case DELETE_TYPE:
				title = getResourceString(mContext, R.string.s_title_deleting)
						.toString();
				break;
			case SEARCH_TYPE:
				title = getResourceString(mContext, R.string.s_title_searching)
						.toString();
				break;
			case COPY_TYPE:
				title = getResourceString(mContext, R.string.s_title_copying)
						.toString();
				break;
			case CUT_TYPE:
				title = getResourceString(mContext, R.string.s_title_moving)
						.toString();
				break;
			case UNZIP_TYPE:
			case UNZIPTO_TYPE:
				title = getResourceString(mContext, R.string.s_title_unzipping)
						.toString();
				break;
			case ZIP_TYPE:
				title = getResourceString(mContext, R.string.s_title_zipping)
						.toString();
				break;
			}
			title += " " + '\u2192' + " " + mIntoPath.getPath();
			return title;
		}

		public String getSubtitle() {
			String subtitle = "";
			if (mInitParams != null && mInitParams.length > 0)
				subtitle = (mInitParams.length > 1 ? mInitParams.length + " "
						+ mContext.getString(R.string.s_files) : mInitParams[0]);
			return subtitle;
		}

		public String getLastRate() {
			if(getStatus() == Status.FINISHED)
				return getResourceString(mContext, R.string.s_complete);
			else if (getStatus() == Status.PENDING)
				return getResourceString(mContext, R.string.s_pending);
			if(isCancelled())
				return getResourceString(mContext, R.string.s_cancelled);
			if (mRemain > 0) {
				Integer min = (int) (mRemain / 60);
				Integer sec = (int) (mRemain % 60);
				return getResourceString(mContext, R.string.s_status_remaining)
						+ (min > 15 ? ">15m" : (min > 0 ? min + ":" : "")
								+ (min > 0 && sec < 10 ? "0" : "") + sec
								+ (min > 0 ? "" : "s"));
			}
			if (mLastRate > 0)
				return getResourceString(mContext, R.string.s_status_rate)
						+ DialogHandler.formatSize(mLastRate).replace(" ", "")
								.toLowerCase() + "/s";
			return "";
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();
			mNotifier.cancel(mNotifyId);
		}

		protected void onPreExecute() {
			boolean showDialog = true, showNotification = false, isCancellable = true;
			notifIcon = R.drawable.icon;
			switch (mType) {
			case DELETE_TYPE:
				showDialog = false;
				break;
			case SEARCH_TYPE:
				notifIcon = android.R.drawable.ic_menu_search;
				break;
			case COPY_TYPE:
				if (mIntoPath.requiresThread())
					notifIcon = android.R.drawable.stat_sys_upload;
				notifIcon = R.drawable.ic_menu_copy;
				showDialog = false;
				showNotification = true;
				break;
			case CUT_TYPE:
				notifIcon = R.drawable.ic_menu_cut;
				showDialog = false;
				showNotification = true;
				break;
			case UNZIP_TYPE:
			case UNZIPTO_TYPE:
				showDialog = false;
				showNotification = true;
				break;
			case ZIP_TYPE:
				showDialog = false;
				showNotification = true;
				break;
			}
			if (showDialog)
				try {
					mPDialog = ProgressDialog.show(mContext, getTitle(),
							getResourceString(mContext, R.string.s_title_wait)
									.toString(), true, true,
							new DialogInterface.OnCancelListener() {
								public void onCancel(DialogInterface dialog) {
									cancelRunningTasks();
								}
							});
				} catch (Exception e) {
				}
			if (showNotification) {
				prepareNotification(notifIcon, isCancellable);
			}
		}

		public int getNotifIconResId() {
			return notifIcon;
		}

		public void showNotification() {
			if (!notifReady)
				prepareNotification(R.drawable.ic_menu_copy, true);
			mNotifier.notify(mNotifyId, mNote);
		}

		@SuppressWarnings("deprecation")
		public void prepareNotification(int notifIcon, boolean isCancellable) {
			boolean showProgress = true;
			try {
				Intent intent = new Intent(mContext, OperationsActivity.class);
				intent.putExtra("TaskId", taskId);
				PendingIntent pendingIntent = PendingIntent.getActivity(
						mContext, OperationsActivity.REQUEST_VIEW, intent, 0);
				mNote = new Notification(notifIcon, getTitle(),
						System.currentTimeMillis());
				if (showProgress) {
					PendingIntent pendingCancel = PendingIntent.getActivity(
							mContext, OperationsActivity.REQUEST_VIEW, intent,
							0);
					RemoteViews noteView = new RemoteViews(
							mContext.getPackageName(), R.layout.notification);
					if (isCancellable)
						noteView.setOnClickPendingIntent(android.R.id.button1,
								pendingCancel);
					else
						noteView.setViewVisibility(android.R.id.button1,
								View.GONE);
					noteView.setImageViewResource(android.R.id.icon,
							R.drawable.icon);
					noteView.setTextViewText(android.R.id.title, getTitle());
					noteView.setTextViewText(android.R.id.text2, getSubtitle());
					noteView.setViewVisibility(android.R.id.closeButton, View.GONE);
					if(SHOW_NOTIFICATION_STATUS)
					{
						noteView.setTextViewText(android.R.id.text1, getLastRate());
						noteView.setProgressBar(android.R.id.progress, 100, 0, true);
					}
					// noteView.setViewVisibility(R.id.title_search, View.GONE);
					// noteView.setViewVisibility(R.id.title_path, View.GONE);
					mNote.contentView = noteView;
					if (!OpenExplorer.BEFORE_HONEYCOMB)
						mNote.tickerView = noteView;
				} else {
					mNote.tickerText = getTitle();
				}
				mNote.contentIntent = pendingIntent;
				mNote.flags |= Notification.FLAG_ONLY_ALERT_ONCE;
				// mNote.flags |= Notification.FLAG_ONGOING_EVENT;
				if (!isCancellable)
					mNote.flags |= Notification.FLAG_NO_CLEAR;
				// mNotifier.notify(mNotifyId, mNote);
				notifReady = true;
			} catch (Exception e) {
				Logger.LogWarning("Couldn't post notification", e);
			}
		}

		public void searchDirectory(OpenPath dir, String pattern,
				ArrayList<String> aList) {
			try {
				for (OpenPath p : dir.listFiles())
					if (p.getName().matches(pattern))
						aList.add(p.getPath());
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				for (OpenPath p : dir.list())
					if (p.isDirectory())
						searchDirectory(p, pattern, aList);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		protected Integer doInBackground(OpenPath... params) {
			int len = params.length;
			int ret = 0;

			switch (mType) {

			case DELETE_TYPE:
				for (int i = 0; i < len; i++)
					ret += mFileMang.deleteTarget(params[i]);
				break;
			case SEARCH_TYPE:
				mSearchResults = new ArrayList<String>();
				searchDirectory(mIntoPath, mInitParams[0], mSearchResults);
				break;
			case COPY_TYPE:
				// / TODO: Add existing file check
				for (OpenPath file : params) {
					if (file.requiresThread()) {
						isDownload = true;
						this.publishProgress();
					}
					try {
						if (copyToDirectory(file, mIntoPath, 0))
							ret++;
					} catch (IOException e) {
						Logger.LogError("Couldn't copy file (" + file.getName()
								+ " to " + mIntoPath.getPath() + ")", e);
					}
				}
				break;
			case CUT_TYPE:
				for (OpenPath file : params) {
					try {
						if (file.requiresThread()) {
							isDownload = true;
							this.publishProgress();
						}
						if (copyToDirectory(file, mIntoPath, 0)) {
							ret++;
							mFileMang.deleteTarget(file);
						}
					} catch (IOException e) {
						Logger.LogError("Couldn't copy file (" + file.getName()
								+ " to " + mIntoPath.getPath() + ")", e);
					}
				}
				break;
			case UNZIPTO_TYPE:
			case UNZIP_TYPE:
				extractZipFiles(params[0], mIntoPath);
				break;

			case ZIP_TYPE:
				mFileMang.setProgressListener(this);
				publishProgress();
				mFileMang.createZipFile(mIntoPath, params);
				break;
			}

			return ret;
		}

		/*
		 * More efficient Channel based copying
		 */
		private Boolean copyFileToDirectory(final OpenFile source,
				OpenFile into, final int total) {
			Logger.LogVerbose("Using Channel copy");
			if (into.isDirectory() || !into.exists())
				into = into.getChild(source.getName());
			final OpenFile dest = (OpenFile) into;
			final boolean[] running = new boolean[] { true };
			final long size = source.length();
			if (size > 50000)
				new Thread(new Runnable() {
					@Override
					public void run() {
						while ((int)dest.length() < total || running[0]) {
							long pos = dest.length();
							publish((int) pos, (int) size, total);
							try {
								Thread.sleep(500);
							} catch (InterruptedException e) {
								running[0] = false;
							}
						}
					}
				}).start();
			boolean ret = dest.copyFrom(source);
			running[0] = false;
			return ret;
		}

		private Boolean copyToDirectory(OpenPath old, OpenPath intoDir,
				int total) throws IOException {
			if (old instanceof OpenFile && !old.isDirectory()
					&& intoDir instanceof OpenFile)
				if (copyFileToDirectory((OpenFile) old, (OpenFile) intoDir,
						total))
					return true;
			if (old instanceof OpenSMB && intoDir instanceof OpenSMB) {
				Logger.LogVerbose("Using OpenSMB Channel copy");
				if (((OpenSMB) old).copyTo((OpenSMB) intoDir))
					return true;
			}
			Logger.LogVerbose("Using Stream copy");
			if (intoDir instanceof OpenCursor) {
				try {
					if (old.isImageFile() && intoDir.getName().equals("Photos")) {
						if (Images.Media.insertImage(
								mContext.getContentResolver(), old.getPath(),
								old.getName(), null) != null)
							return true;
					}
				} catch (Exception e) {
					return false;
				}
			}
			OpenPath newDir = intoDir;
			if(intoDir instanceof OpenSmartFolder)
			{
				newDir = ((OpenSmartFolder)intoDir).getFirstDir();
				if(old instanceof OpenFile && newDir instanceof OpenFile)
					return copyFileToDirectory((OpenFile)old, (OpenFile)newDir, total);
			}
			Logger.LogDebug("Trying to copy [" + old.getPath() + "] to ["
					+ intoDir.getPath() + "]...");
			if (old.getPath().equals(intoDir.getPath())) {
				return false;
			}
			if (old.isDirectory()) {
				newDir = newDir.getChild(old.getName());
				if (!newDir.exists() && !newDir.mkdir()) {
					Logger.LogWarning("Couldn't create initial destination file.");
				}
			}
			byte[] data = new byte[FileManager.BUFFER];
			int read = 0;

			if (old.isDirectory() && newDir.isDirectory() && newDir.canWrite()) {
				OpenPath[] files = old.list();

				for (OpenPath file : files)
					total += (int) file.length();

				for (int i = 0; i < files.length; i++)
					if (!copyToDirectory(files[i], newDir, total)) {
						Logger.LogWarning("Couldn't copy " + files[i].getName()
								+ ".");
						return false;
					}

				return true;

				/*
				 * } else if(old instanceof OpenSMB && newDir instanceof
				 * OpenFile) { ((OpenSMB)old).copyTo((OpenFile)newDir, this);
				 */
			} else if (old.isFile() && newDir.isDirectory()
					&& newDir.canWrite()) {
				OpenPath newFile = newDir.getChild(old.getName());
				if (newFile.getPath().equals(old.getPath())) {
					Logger.LogWarning("Old = new");
					return false;
				}
				Logger.LogDebug("Creating File [" + newFile.getPath() + "]...");
				if (!newDir.exists() && !newFile.mkdir()) {
					Logger.LogWarning("Couldn't create initial destination file.");
					return false;
				}

				int size = (int) old.length();
				int pos = 0;

				BufferedInputStream i_stream = null;
				BufferedOutputStream o_stream = null;
				boolean success = false;
				try {
					Logger.LogDebug("Writing " + newFile.getPath());
					i_stream = new BufferedInputStream(old.getInputStream());
					o_stream = new BufferedOutputStream(
							newFile.getOutputStream());

					while ((read = i_stream.read(data, 0, FileManager.BUFFER)) != -1) {
						o_stream.write(data, 0, read);
						pos += FileManager.BUFFER;
						publishMyProgress(pos, size);
					}

					o_stream.flush();
					i_stream.close();
					o_stream.close();

					success = true;

				} catch (NullPointerException e) {
					Logger.LogError("Null pointer trying to copy file.", e);
				} catch (FileNotFoundException e) {
					Logger.LogError("Couldn't find file to copy.", e);
				} catch (IOException e) {
					Logger.LogError("IOException copying file.", e);
				} catch (Exception e) {
					Logger.LogError("Unknown error copying file.", e);
				} finally {
					if (o_stream != null)
						o_stream.close();
					if (i_stream != null)
						i_stream.close();
				}
				return success;

			} else if (!newDir.canWrite()) {
				Logger.LogWarning("Destination directory not writable.");
				return false;
			}

			Logger.LogWarning("Couldn't copy file for unknown reason.");
			return false;
		}

		public void extractZipFiles(OpenPath zip, OpenPath directory) {
			byte[] data = new byte[FileManager.BUFFER];
			ZipEntry entry;
			ZipInputStream zipstream;

			if (!directory.mkdir())
				return;

			try {
				zipstream = new ZipInputStream(zip.getInputStream());

				while ((entry = zipstream.getNextEntry()) != null) {
					OpenPath newFile = directory.getChild(entry.getName());
					if (!newFile.mkdir())
						continue;

					int read = 0;
					FileOutputStream out = (FileOutputStream) newFile
							.getOutputStream();
					while ((read = zipstream.read(data, 0, FileManager.BUFFER)) != -1)
						out.write(data, 0, read);

					zipstream.closeEntry();
					out.close();
				}

			} catch (FileNotFoundException e) {
				e.printStackTrace();

			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		public void publish(int current, int size, int total) {
			publishProgress(current, size, total);
			//OnWorkerProgressUpdate(current, total);
		}

		private long lastPublish = 0l;

		public void publishMyProgress(Integer... values) {
			if (new Date().getTime() - lastPublish < 500)
				return;
			lastPublish = new Date().getTime();
			publishProgress(values);
		}

		public int getProgressA() {
			return (int) (((float) mLastProgress[0] / (float) mLastProgress[1]) * 1000f);
		}

		public int getProgressB() {
			return (int) (((float) mLastProgress[0] / (float) mLastProgress[2]) * 1000f);
		}

		@SuppressWarnings("unused")
		@Override
		protected void onProgressUpdate(Integer... values) {
			int current = 0, size = 0, total = 0;
			if (values.length > 0)
				current = size = total = values[0];
			if (values.length > 1)
				size = total = values[1];
			if (values.length > 2)
				total = Math.max(total, values[2]);

			// if(mThreadListener != null)
			// mThreadListener.onWorkerProgressUpdate(current, size);

			mLastProgress[0] = current;
			mLastProgress[1] = size;
			mLastProgress[2] = total;

			if (size == 0)
				size = 1;
			if (total == 0)
				total = 1;

			int progA = (int) (((float) current / (float) size) * 1000f);
			int progB = (int) (((float) current / (float) total) * 1000f);

			long running = new Date().getTime() - mStart.getTime();
			if (running / 1000 > 0) {
				mLastRate = ((long) current) / (running / 1000);
				if (mLastRate > 0)
					mRemain = Utils.getAverage(mRemain, (long) (size - current)
							/ mLastRate);
			}
			
			//publish(current, size, total);
			OnWorkerProgressUpdate(current, total);

			// Logger.LogInfo("onProgressUpdate(" + current + ", " + size + ", "
			// + total + ")-("
			// + progA + "," + progB + ")-> " + mRemain + "::" + mLastRate);

			// mNote.setLatestEventInfo(mContext, , contentText, contentIntent)

			try {
				if (values.length == 0)
					mPDialog.setIndeterminate(true);
				else {
					mPDialog.setIndeterminate(false);
					mPDialog.setMax(1000);
					mPDialog.setProgress(progA);
					if (progB != progA)
						mPDialog.setSecondaryProgress(progB);
					else
						mPDialog.setSecondaryProgress(0);
				}
			} catch (Exception e) {
			}
			
			if(SHOW_NOTIFICATION_STATUS) {

			try {
				RemoteViews noteView = mNote.contentView;
				noteView.setTextViewText(android.R.id.text1, getLastRate());
				if (values.length == 0 && isDownload)
					noteView.setImageViewResource(android.R.id.icon,
							android.R.drawable.stat_sys_download);
				else
					noteView.setImageViewResource(android.R.id.icon,
							android.R.drawable.stat_notify_sync);
				noteView.setProgressBar(android.R.id.progress, 1000, progA,
						values.length == 0);
				mNotifier.notify(mNotifyId, mNote);
				// noteView.notify();
				// noteView.
			} catch (Exception e) {
				Logger.LogWarning("Couldn't update notification progress.", e);
			}
			
			}
		}

		public void updateView(final View view) {
			if (view == null)
				return;
			Runnable runnable = new Runnable() {
				public void run() {
					if (view.findViewById(android.R.id.icon) != null)
						((ImageView) view.findViewById(android.R.id.icon))
								.setImageResource(getNotifIconResId());
					if (view.findViewById(android.R.id.title) != null)
						((TextView) view.findViewById(android.R.id.title))
								.setText(getTitle());
					if (view.findViewById(android.R.id.text1) != null)
						((TextView) view.findViewById(android.R.id.text1))
								.setText(getLastRate());
					if (view.findViewById(android.R.id.text2) != null)
						((TextView) view.findViewById(android.R.id.text2))
								.setText(getSubtitle());
					if (view.findViewById(android.R.id.progress) != null) {
						int progA = (int) (((float) mLastProgress[0] / (float) mLastProgress[1]) * 1000f);
						int progB = (int) (((float) mLastProgress[0] / (float) mLastProgress[2]) * 1000f);
						if(getStatus() == Status.FINISHED)
							MenuUtils.setViewsVisible(view, false, android.R.id.closeButton, android.R.id.progress);
						else {
							ProgressBar pb = (ProgressBar) view
									.findViewById(android.R.id.progress);
							pb.setIndeterminate(mLastRate == 0);
							pb.setMax(1000);
							pb.setProgress(progA);
							pb.setSecondaryProgress(progB);
						}
					}
				}
			};
			if (!Thread.currentThread().equals(OpenExplorer.UiThread))
				view.post(runnable);
			else
				runnable.run();
		}

		@Override
		public void onProgressUpdateCallback(Integer... vals) {
			publishMyProgress(vals);
		}

		protected void onPostExecute(Integer result) {
			// NotificationManager mNotifier =
			// (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
			BackgroundWork[] tasks = getRunningTasks();
			if (tasks.length == 0 || tasks[0].equals(this))
				mNotifier.cancel(mNotifyId);

			if (mPDialog != null && mPDialog.isShowing())
				mPDialog.dismiss();

			switch (mType) {

			case DELETE_TYPE:
				if (result == 0)
					Toast.makeText(
							mContext,
							getResourceString(mContext, R.string.s_msg_none,
									R.string.s_msg_deleted), Toast.LENGTH_SHORT)
							.show();
				else if (result == -1)
					Toast.makeText(
							mContext,
							getResourceString(mContext, R.string.s_msg_some,
									R.string.s_msg_deleted), Toast.LENGTH_SHORT)
							.show();
				else
					Toast.makeText(
							mContext,
							getResourceString(mContext, R.string.s_msg_all,
									R.string.s_msg_deleted), Toast.LENGTH_SHORT)
							.show();

				break;

			case SEARCH_TYPE:
				OnWorkerThreadComplete(mType, mSearchResults);
				return;

			case COPY_TYPE:
				if (result == null || result == 0)
					Toast.makeText(
							mContext,
							getResourceString(mContext, R.string.s_msg_none,
									R.string.s_msg_copied), Toast.LENGTH_SHORT)
							.show();
				else if (result != null && result < 0)
					Toast.makeText(
							mContext,
							getResourceString(mContext, R.string.s_msg_some,
									R.string.s_msg_copied), Toast.LENGTH_SHORT)
							.show();
				else
					Toast.makeText(
							mContext,
							getResourceString(mContext, R.string.s_msg_all,
									R.string.s_msg_copied), Toast.LENGTH_SHORT)
							.show();
				break;
			case CUT_TYPE:
				if (result == null || result == 0)
					Toast.makeText(
							mContext,
							getResourceString(mContext, R.string.s_msg_none,
									R.string.s_msg_moved), Toast.LENGTH_SHORT)
							.show();
				else if (result != null && result < 0)
					Toast.makeText(
							mContext,
							getResourceString(mContext, R.string.s_msg_some,
									R.string.s_msg_moved), Toast.LENGTH_SHORT)
							.show();
				else
					Toast.makeText(
							mContext,
							getResourceString(mContext, R.string.s_msg_all,
									R.string.s_msg_moved), Toast.LENGTH_SHORT)
							.show();

				break;

			case UNZIPTO_TYPE:
				break;

			case UNZIP_TYPE:
				break;

			case ZIP_TYPE:
				break;
			}
			OnWorkerThreadComplete(mType, null);
		}
	}

}
