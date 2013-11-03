/*
 *      Copyright (C) 2005-2015 Team XBMC
 *      http://xbmc.org
 *
 *  This Program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2, or (at your option)
 *  any later version.
 *
 *  This Program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with XBMC Remote; see the file license.  If not, write to
 *  the Free Software Foundation, 675 Mass Ave, Cambridge, MA 02139, USA.
 *  http://www.gnu.org/copyleft/gpl.html
 *
 */

package org.xbmc.android.jsonrpc.service;

import org.xbmc.android.jsonrpc.api.call.AudioLibrary;
import org.xbmc.android.jsonrpc.api.model.AudioModel.AlbumFields;
import org.xbmc.android.jsonrpc.config.HostConfig;
import org.xbmc.android.jsonrpc.io.ConnectionManager;
import org.xbmc.android.jsonrpc.io.ConnectionManager.HandlerCallback;
import org.xbmc.android.jsonrpc.io.audio.AlbumHandler;
import org.xbmc.android.jsonrpc.io.audio.ArtistHandler;
import org.xbmc.android.jsonrpc.provider.AudioProvider;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.util.Log;

/**
 * Background {@link Service} that synchronizes data living in
 * {@link AudioProvider}.
 * <p>
 * This class, along with the other ones in this package was closely inspired by
 * Google's official iosched app, see http://code.google.com/p/iosched/
 *
 * @author freezy <freezy@xbmc.org>
 */
public class AudioSyncService extends AbstractSyncService {

	private static final String TAG = AudioSyncService.class.getSimpleName();
	public static final String EXTRA_STATUS_RECEIVER = "org.xbmc.android.jsonprc.extra.STATUS_RECEIVER";

	private long mStart = 0;

	private ConnectionManager mCm = null;


	@Override
	public void onCreate() {
		super.onCreate();
		Log.d(TAG, "Starting AudioSyncService...");
		mCm = new ConnectionManager(getApplicationContext(), new HostConfig(HOST));
	}


	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(TAG, "Starting quering...");
		mReceiver = intent.getParcelableExtra(EXTRA_STATUS_RECEIVER);
		if (mReceiver != null) {
			mReceiver.send(STATUS_RUNNING, Bundle.EMPTY);
		} else {
			Log.w(TAG, "Receiver is null, cannot post back data!");
		}

		// start quering...
		mStart = System.currentTimeMillis();
		syncArtists();
		return START_STICKY;
	}

	private void syncArtists() {
		final AudioLibrary.GetArtists getArtistsCall = new AudioLibrary.GetArtists();
		mCm.call(getArtistsCall, new ArtistHandler(), new HandlerCallback() {
			@Override
			public void onFinish() {
				Log.i(TAG, "Artists successfully synced in " + (System.currentTimeMillis() - mStart) + "ms, starting albums.");
				syncAlbums();
			}

			@Override
			public void onError(String message, String hint) {
				AudioSyncService.this.onError(message + " " + hint);
				stopSelf();
			}

		});
	}

	private void syncAlbums() {
		final AudioLibrary.GetAlbums getAlbumsCall = new AudioLibrary.GetAlbums(AlbumFields.TITLE, AlbumFields.ARTISTID, AlbumFields.YEAR);
		mCm.call(getAlbumsCall, new AlbumHandler(), new HandlerCallback() {

			@Override
			public void onFinish() {
				Log.i(TAG, "Albums successfully synced too! Total time: " + (System.currentTimeMillis() - mStart) + "ms.");
				if (mReceiver != null) {
					// Pass back result to surface listener
					mReceiver.send(STATUS_FINISHED, null);
				}
				mCm.disconnect();
				stopSelf();
			}

			@Override
			public void onError(String message, String hint) {
				AudioSyncService.this.onError(message + " " + hint);
				stopSelf();
			}
		});
	}
}
