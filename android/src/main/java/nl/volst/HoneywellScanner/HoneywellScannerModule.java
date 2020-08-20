package nl.volst.HoneywellScanner;

import java.lang.reflect.Method;
import java.util.Set;

import javax.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.Promise;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import static nl.volst.HoneywellScanner.HoneywellScannerPackage.TAG;

import com.honeywell.aidc.AidcManager;
import com.honeywell.aidc.AidcManager.CreatedCallback;
import com.honeywell.aidc.BarcodeFailureEvent;
import com.honeywell.aidc.BarcodeReadEvent;
import com.honeywell.aidc.BarcodeReader;
import com.honeywell.aidc.ScannerNotClaimedException;
import com.honeywell.aidc.ScannerUnavailableException;
import com.honeywell.aidc.UnsupportedPropertyException;

@SuppressWarnings("unused")
public class HoneywellScannerModule extends ReactContextBaseJavaModule implements BarcodeReader.BarcodeListener, LifecycleEventListener {

	// Debugging
	private static final boolean D = true;
    private boolean triggerState = false;

	private static BarcodeReader barcodeReader;
	private AidcManager manager;
	private BarcodeReader reader;
	private ReactApplicationContext mReactContext;

	private static final String BARCODE_READ_SUCCESS = "barcodeReadSuccess";
	private static final String BARCODE_READ_FAIL = "barcodeReadFail";

	public HoneywellScannerModule(ReactApplicationContext reactContext) {
		super(reactContext);
		mReactContext = reactContext;
		mReactContext.addLifecycleEventListener(this);
	}

	@Override
	public String getName() {
		return "HoneywellScanner";
	}


	@Override
	public void onHostResume() {
		if (reader != null) {
			try {
				reader.claim();
			} catch (ScannerUnavailableException e) {
				//
			}
		}
	}

	@Override
	public void onHostPause() {
		if (reader != null) {
			// release the scanner claim so we don't get any scanner
			// notifications while paused.
			reader.release();
		}
	}

	@Override
	public void onHostDestroy() {
		if (reader != null) {
			reader.removeBarcodeListener(this);
			reader.close();

			reader = null;
		}

		if (manager != null) {
			manager.close();

			manager = null;
		}
	}

	/**
	 * Send event to javascript
	 *
	 * @param eventName Name of the event
	 * @param params    Additional params
	 */
	private void sendEvent(String eventName, @Nullable WritableMap params) {
		if (mReactContext.hasActiveCatalystInstance()) {
			if (D) Log.d(TAG, "Sending event: " + eventName);
			mReactContext
					.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
					.emit(eventName, params);
		}
	}

	public void onBarcodeEvent(BarcodeReadEvent barcodeReadEvent) {
		if (D) Log.d(TAG, "HONEYWELLSCANNER - Barcode scan read");
		WritableMap params = Arguments.createMap();
		params.putString("data", barcodeReadEvent.getBarcodeData());
		sendEvent(BARCODE_READ_SUCCESS, params);
		try {
            reader.aim(!triggerState);
            reader.light(!triggerState);
            reader.decode(!triggerState);
            triggerState = !triggerState;
        } catch (ScannerNotClaimedException e) {
            e.printStackTrace();
        } catch (ScannerUnavailableException e) {
            e.printStackTrace();
        }
	}

	public void onFailureEvent(BarcodeFailureEvent barcodeFailureEvent) {
		if (D) Log.d(TAG, "HONEYWELLSCANNER - Barcode scan failed");
		sendEvent(BARCODE_READ_FAIL, null);
	}

	/*******************************/
	/** Methods Available from JS **/
	/*******************************/

	@ReactMethod
	public void startReader(final Promise promise) {
		AidcManager.create(mReactContext, new CreatedCallback() {
			@Override
			public void onCreated(AidcManager aidcManager) {
				manager = aidcManager;

				if (reader != null) {
					reader.release();
					reader.close();
				}

				reader = manager.createBarcodeReader();
				reader.addBarcodeListener(HoneywellScannerModule.this);
				try {
					reader.claim();

					// apply settings
					reader.setProperty(BarcodeReader.PROPERTY_EAN_13_ENABLED, true);
					reader.setProperty(BarcodeReader.PROPERTY_EAN_13_CHECK_DIGIT_TRANSMIT_ENABLED, true);
					reader.setProperty(BarcodeReader.PROPERTY_EAN_8_ENABLED, true);
					reader.setProperty(BarcodeReader.PROPERTY_EAN_8_CHECK_DIGIT_TRANSMIT_ENABLED, true);
					reader.setProperty(BarcodeReader.PROPERTY_CODE_39_ENABLED, false);
					reader.setProperty(BarcodeReader.PROPERTY_DATAMATRIX_ENABLED, true);
					reader.setProperty(BarcodeReader.PROPERTY_DATA_PROCESSOR_LAUNCH_BROWSER, false);

					// set the trigger mode to client control
					reader.setProperty(BarcodeReader.PROPERTY_TRIGGER_CONTROL_MODE,
							BarcodeReader.TRIGGER_CONTROL_MODE_AUTO_CONTROL);

					promise.resolve(true);
				} catch (ScannerUnavailableException e) {
					promise.reject(e);
				} catch (UnsupportedPropertyException e) {
					promise.reject(e);
				} catch (Exception e) {
					promise.reject(e);
				}
			}
		});
	}

	@ReactMethod
	public void stopReader(Promise promise) {
		if (reader != null) {
			reader.close();
		}
		if (manager != null) {
			manager.close();
		}
		promise.resolve(null);
	}

	@ReactMethod
	public void StartScan(Promise promise) {
		if (reader != null) {
			try {
				reader.softwareTrigger(triggerState);
				reader.aim(!triggerState);
				reader.light(!triggerState);
				reader.decode(!triggerState);
				triggerState = !triggerState;

				promise.resolve(true);
			} catch (ScannerNotClaimedException e) {
				// TODO Auto-generated catch block
				promise.reject(e);
			} catch (ScannerUnavailableException e) {
				// TODO Auto-generated catch block
				promise.reject(e);
			}
		}
	}

	@ReactMethod
	public void StopScan(Promise promise) {
		if (reader != null) {
			try {
				reader.softwareTrigger(false);
				promise.resolve(true);
			} catch (ScannerNotClaimedException e) {
				// TODO Auto-generated catch block
				promise.reject(e);
			} catch (ScannerUnavailableException e) {
				// TODO Auto-generated catch block
				promise.reject(e);
			}
		}
	}

	@ReactMethod
	public void checkCompatible(Promise promise) {
		if (Build.BRAND.toLowerCase().contains("honeywell")) {
			promise.resolve(true);
		} else {
			promise.resolve(false);
		}
	}

	@Override
	public Map<String, Object> getConstants() {
		final Map<String, Object> constants = new HashMap<>();
		constants.put("BARCODE_READ_SUCCESS", BARCODE_READ_SUCCESS);
		constants.put("BARCODE_READ_FAIL", BARCODE_READ_FAIL);
		return constants;
	}

}
