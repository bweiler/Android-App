/*
 * Copyright (c) 2015, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.nordicsemi.android.blinky.profile;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;

import java.util.Deque;
import java.util.LinkedList;
import java.util.UUID;

import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.log.LogContract;

public class BlinkyManager extends BleManager<BlinkyManagerCallbacks> {
	/**
	 * Nordic Blinky Service UUID
	 */
	public final static UUID LBS_UUID_SERVICE = UUID.fromString("00001523-1212-efde-1523-785feabcd123");
	/**
	 * 1 Byte DATA characteristic UUID
	 */
	private final static UUID LBS_UUID_DATA_CHAR = UUID.fromString("00001524-1212-efde-1523-785feabcd123");
	/**
	 * 1 Byte CMD characteristic UUID
	 */
	private final static UUID LBS_UUID_CMD_CHAR = UUID.fromString("00001525-1212-efde-1523-785feabcd123");

	private final static UUID LBS_UUID_BYTE2_CHAR = UUID.fromString("00001526-1212-efde-1523-785feabcd123");
	private final static UUID LBS_UUID_BYTE128_CHAR = UUID.fromString("00001527-1212-efde-1523-785feabcd123");
	private final static UUID LBS_UUID_BYTE4_CHAR = UUID.fromString("00001528-1212-efde-1523-785feabcd123");

	private BluetoothGattCharacteristic mDATACharacteristic, mCMDCharacteristic, mBYTE2Characteristic,
			mBYTE128Characteristic, mBYTE4Characteristic;

	public BlinkyManager(final Context context) {
		super(context);
	}

	@Override
	protected BleManagerGattCallback getGattCallback() {
		return mGattCallback;
	}

	@Override
	protected boolean shouldAutoConnect() {
		// If you want to connect to the device using autoConnect flag = true, return true here.
		// Read the documentation of this method.
		return super.shouldAutoConnect();
	}

	/**
	 * BluetoothGatt callbacks for connection/disconnection, service discovery, receiving indication, etc
	 */
	private final BleManagerGattCallback mGattCallback = new BleManagerGattCallback() {

		@Override
		protected Deque<Request> initGatt(final BluetoothGatt gatt) {
			final LinkedList<Request> requests = new LinkedList<>();
			requests.push(Request.newReadRequest(mCMDCharacteristic));
			requests.push(Request.newReadRequest(mDATACharacteristic));
			requests.push(Request.newReadRequest(mBYTE2Characteristic));
			requests.push(Request.newReadRequest(mBYTE128Characteristic));
			requests.push(Request.newReadRequest(mBYTE4Characteristic));
			requests.push(Request.newEnableNotificationsRequest(mDATACharacteristic));
			requests.push(Request.newEnableNotificationsRequest(mBYTE128Characteristic));
			requests.push(Request.newEnableNotificationsRequest(mBYTE2Characteristic));
			return requests;
		}

		@Override
		public boolean isRequiredServiceSupported(final BluetoothGatt gatt) {
			final BluetoothGattService service = gatt.getService(LBS_UUID_SERVICE);
			if (service != null) {
				mDATACharacteristic = service.getCharacteristic(LBS_UUID_DATA_CHAR);
				mCMDCharacteristic = service.getCharacteristic(LBS_UUID_CMD_CHAR);
				mBYTE2Characteristic = service.getCharacteristic(LBS_UUID_BYTE2_CHAR);
				mBYTE128Characteristic = service.getCharacteristic(LBS_UUID_BYTE128_CHAR);
				mBYTE4Characteristic = service.getCharacteristic(LBS_UUID_BYTE4_CHAR);
			}

			boolean writeRequest = false;
			if (mCMDCharacteristic != null) {
				final int rxProperties = mCMDCharacteristic.getProperties();
				writeRequest = (rxProperties & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0;
			}

			return mDATACharacteristic != null && mCMDCharacteristic != null && writeRequest;
		}

		@Override
		protected void onDeviceDisconnected() {
			mDATACharacteristic = null;
			mCMDCharacteristic = null;
			mBYTE2Characteristic = null;
			mBYTE128Characteristic = null;
			mBYTE4Characteristic = null;

		}

		@Override
		protected void onCharacteristicRead(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
			if (characteristic == mCMDCharacteristic) {
				final int data = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
				final byte CMDByte = (byte) data;
				log(LogContract.Log.Level.APPLICATION, "Command byte read" + CMDByte + " ");
				mCallbacks.onDataSent(CMDByte);
			}
			if (characteristic == mDATACharacteristic) {
				final int data = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
				final byte datain = (byte) data;
				log(LogContract.Log.Level.APPLICATION, "1 byte data recieved");
				mCallbacks.onDataReceived(datain);
			}
			if (characteristic == mBYTE4Characteristic) {
				final byte[] datain = characteristic.getValue();
				log(LogContract.Log.Level.APPLICATION, "4 byte data read now recieved : " + datain.length);
				mCallbacks.onByte4Sent(datain);
			}
			if (characteristic == mBYTE128Characteristic) {
				final byte[] datain = characteristic.getValue();
				log(LogContract.Log.Level.APPLICATION, "20 byte data read now recieved : " + datain.length);
				mCallbacks.onByte128Read(datain);
			}
		}

		@Override
		public void onCharacteristicWrite(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
			// This method is only called for LED characteristic
			if (characteristic == mDATACharacteristic) {
				final int data = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
				final byte CMDByte = (byte) data;
				log(LogContract.Log.Level.APPLICATION, "1 Byte Command written" + CMDByte + " ");
				mCallbacks.onDataSent(CMDByte);
			}
			if (characteristic == mBYTE128Characteristic) {
				final byte[] data = characteristic.getValue();
				log(LogContract.Log.Level.APPLICATION, "Byte128 write command " + data.length);
				mCallbacks.onByte128Sent(data);
			}
			if (characteristic == mBYTE4Characteristic) {
				final byte[] data = characteristic.getValue();
				log(LogContract.Log.Level.APPLICATION, "Byte4 write command " + data.length);
				mCallbacks.onByte4Sent(data);
			}
		}

		@Override
		public void onCharacteristicNotified(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
			if (characteristic == mDATACharacteristic) {
				final int data = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
				final byte datain = (byte) data;
				log(LogContract.Log.Level.APPLICATION, "1 byte Data received in notify");
				mCallbacks.onDataReceived(datain);
			}
			if (characteristic == mBYTE128Characteristic) {
				final byte[] data = characteristic.getValue();
				log(LogContract.Log.Level.APPLICATION, "Byte128 received in notify = " + data.length );
				mCallbacks.onByte128Sent(data);
			}
			if (characteristic == mBYTE2Characteristic) {
				final byte[] data = characteristic.getValue();
				log(LogContract.Log.Level.APPLICATION, "Byte2 received in notify = " + data.length);
				mCallbacks.onByte2Sent(data);
			}
		}
	};

	public void send(final Byte CMDByte) {
		// Are we connected?
		if (mCMDCharacteristic == null)
			return;

		final int command = CMDByte.intValue();
		mCMDCharacteristic.setValue(command,BluetoothGattCharacteristic.FORMAT_UINT8,0);
		log(LogContract.Log.Level.WARNING, "Command " + CMDByte  + "...");
		writeCharacteristic(mCMDCharacteristic);
	}

	public void readin() {
		// Are we connected?
		if (mBYTE128Characteristic == null) {
			return;
		}
		log(LogContract.Log.Level.WARNING, "Reading 20 bytes value");
		readCharacteristic(mBYTE128Characteristic);
	}

	public void readdata() {
		// Are we connected?
		if (mDATACharacteristic == null) {
			return;
		}
		log(LogContract.Log.Level.WARNING, "Reading byte value");
		readCharacteristic(mDATACharacteristic);
	}

	public void send4(final byte[] byte4) {
		// Are we connected?
		if (mBYTE4Characteristic == null)
			return;

		mBYTE4Characteristic.setValue(byte4);
		log(LogContract.Log.Level.WARNING, "4 Byte...");
		readCharacteristic(mBYTE4Characteristic);
	}
}
