//
//  LuaLoader.java
//  TemplateApp
//
//  Copyright (c) 2012 __MyCompanyName__. All rights reserved.
//

// This corresponds to the name of the Lua library,
// e.g. [Lua] require "plugin.library"
package plugin.amplitudeRecorder;

import android.media.MediaRecorder;
import android.os.Handler;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.ansca.corona.CoronaActivity;
import com.ansca.corona.CoronaEnvironment;
import com.ansca.corona.CoronaLua;
import com.ansca.corona.CoronaRuntime;
import com.ansca.corona.CoronaRuntimeListener;
import com.ansca.corona.CoronaRuntimeTask;
import com.ansca.corona.CoronaRuntimeTaskDispatcher;
import com.naef.jnlua.JavaFunction;
import com.naef.jnlua.LuaState;
import com.naef.jnlua.NamedJavaFunction;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import static java.lang.StrictMath.log10;


/**
 * Implements the Lua interface for a Corona plugin.
 * <p>
 * Only one instance of this class will be created by Corona for the lifetime of the application.
 * This instance will be re-used for every new Corona activity that gets created.
 */
@SuppressWarnings("WeakerAccess")
public class LuaLoader implements JavaFunction, CoronaRuntimeListener {
	/** Lua registry ID to the Lua function to be called when the ad request finishes. */
	private int fListener;
	private MediaRecorder recorder;
	private Timer loopGetAmplitude;
	private CoronaRuntimeTaskDispatcher recDis;
	private int recLis;
	private boolean isRecording = false;


	/**
	 * Creates a new Lua interface to this plugin.
	 * <p>
	 * Note that a new LuaLoader instance will not be created for every CoronaActivity instance.
	 * That is, only one instance of this class will be created for the lifetime of the application process.
	 * This gives a plugin the option to do operations in the background while the CoronaActivity is destroyed.
	 */
	@SuppressWarnings("unused")
	public LuaLoader() {
		// Initialize member variables.
		fListener = CoronaLua.REFNIL;

		// Set up this plugin to listen for Corona runtime events to be received by methods
		// onLoaded(), onStarted(), onSuspended(), onResumed(), and onExiting().
		CoronaEnvironment.addRuntimeListener(this);
	}

	/**
	 * Called when this plugin is being loaded via the Lua require() function.
	 * <p>
	 * Note that this method will be called every time a new CoronaActivity has been launched.
	 * This means that you'll need to re-initialize this plugin here.
	 * <p>
	 * Warning! This method is not called on the main UI thread.
	 * @param L Reference to the Lua state that the require() function was called from.
	 * @return Returns the number of values that the require() function will return.
	 *         <p>
	 *         Expected to return 1, the library that the require() function is loading.
	 */
	@Override
	public int invoke(LuaState L) {
		// Register this plugin into Lua with the following functions.
		NamedJavaFunction[] luaFunctions = new NamedJavaFunction[] {
			new record(),
			new stopRecording(),
		};
		String libName = L.toString( 1 );
		L.register(libName, luaFunctions);

		// Returning 1 indicates that the Lua require() function will return the above Lua library.
		return 1;
	}

	/**
	 * Called after the Corona runtime has been created and just before executing the "main.lua" file.
	 * <p>
	 * Warning! This method is not called on the main thread.
	 * @param runtime Reference to the CoronaRuntime object that has just been loaded/initialized.
	 *                Provides a LuaState object that allows the application to extend the Lua API.
	 */
	@Override
	public void onLoaded(CoronaRuntime runtime) {
		// Note that this method will not be called the first time a Corona activity has been launched.
		// This is because this listener cannot be added to the CoronaEnvironment until after
		// this plugin has been required-in by Lua, which occurs after the onLoaded() event.
		// However, this method will be called when a 2nd Corona activity has been created.

	}

	/**
	 * Called just after the Corona runtime has executed the "main.lua" file.
	 * <p>
	 * Warning! This method is not called on the main thread.
	 * @param runtime Reference to the CoronaRuntime object that has just been started.
	 */
	@Override
	public void onStarted(CoronaRuntime runtime) {
	}

	/**
	 * Called just after the Corona runtime has been suspended which pauses all rendering, audio, timers,
	 * and other Corona related operations. This can happen when another Android activity (ie: window) has
	 * been displayed, when the screen has been powered off, or when the screen lock is shown.
	 * <p>
	 * Warning! This method is not called on the main thread.
	 * @param runtime Reference to the CoronaRuntime object that has just been suspended.
	 */
	@Override
	public void onSuspended(CoronaRuntime runtime) {
		if (recorder != null && isRecording == true){
			recorder.stop();
			recDis.send(new CoronaRuntimeTask() {
				@Override
				public void executeUsing(CoronaRuntime coronaRuntime) {
					LuaState l = coronaRuntime.getLuaState();
					CoronaLua.newEvent(l, "amplitudeRecorder");
					l.pushString("stopped");
					l.setField(-2, "status");
					try {
						CoronaLua.dispatchEvent(l,recLis, 0);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
			loopGetAmplitude.cancel();
			loopGetAmplitude.purge();
		}
	}

	/**
	 * Called just after the Corona runtime has been resumed after a suspend.
	 * <p>
	 * Warning! This method is not called on the main thread.
	 * @param runtime Reference to the CoronaRuntime object that has just been resumed.
	 */
	@Override
	public void onResumed(CoronaRuntime runtime) {
	}

	/**
	 * Called just before the Corona runtime terminates.
	 * <p>
	 * This happens when the Corona activity is being destroyed which happens when the user presses the Back button
	 * on the activity, when the native.requestExit() method is called in Lua, or when the activity's finish()
	 * method is called. This does not mean that the application is exiting.
	 * <p>
	 * Warning! This method is not called on the main thread.
	 * @param runtime Reference to the CoronaRuntime object that is being terminated.
	 */
	@Override
	public void onExiting(CoronaRuntime runtime) {
		// Remove the Lua listener reference.
		if (recorder != null){
			recorder.stop();
			recDis.send(new CoronaRuntimeTask() {
				@Override
				public void executeUsing(CoronaRuntime coronaRuntime) {
					LuaState l = coronaRuntime.getLuaState();
					CoronaLua.newEvent(l, "amplitudeRecorder");
					l.pushString("stopped");
					l.setField(-2, "status");
					try {
						CoronaLua.dispatchEvent(l,recLis, 0);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
			loopGetAmplitude.cancel();
			loopGetAmplitude.purge();
		}

		CoronaLua.deleteRef( runtime.getLuaState(), fListener );
		fListener = CoronaLua.REFNIL;
	}




	@SuppressWarnings("unused")
	private class record implements NamedJavaFunction {
		/**
		 * Gets the name of the Lua function as it would appear in the Lua script.
		 * @return Returns the name of the custom Lua function.
		 */
		@Override
		public String getName() {
			return "record";
		}
		
		/**
		 * This method is called when the Lua function is called.
		 * <p>
		 * Warning! This method is not called on the main UI thread.
		 * @param L Reference to the Lua state.
		 *                 Needed to retrieve the Lua function's parameters and to return values back to Lua.
		 * @return Returns the number of values to be returned by the Lua function.
		 */
		@Override
		public int invoke(LuaState L) {
			recorder = new MediaRecorder();
			recDis = new CoronaRuntimeTaskDispatcher(L);
			recLis = CoronaLua.newRef(L, 2);
			// following calls throw Illegal State Exceptions, but here we follow the proper order

			recorder.setAudioSource(MediaRecorder.AudioSource.MIC);

			// After set audio source
			recorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);

			// After set output format
			recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
			recorder.setOutputFile(L.toString(1));
			// Before prepare

			try {
				recorder.prepare();
			} catch (IOException e) {
				System.err.println(e);
			}
			// After prepare
			recorder.start();
			recDis.send(new CoronaRuntimeTask() {
				@Override
				public void executeUsing(CoronaRuntime coronaRuntime) {
					LuaState l = coronaRuntime.getLuaState();
					CoronaLua.newEvent(l, "amplitudeRecorder");
					l.pushString("recording");
					l.setField(-2, "status");
					try {
						CoronaLua.dispatchEvent(l,recLis, 0);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});

			recorder.getMaxAmplitude();
			class GetAmps extends TimerTask {
				public void run() {
					recDis.send(new CoronaRuntimeTask() {
						@Override
						public void executeUsing(CoronaRuntime coronaRuntime) {
							LuaState l = coronaRuntime.getLuaState();
							CoronaLua.newEvent(l, "amplitudeRecorder");
							l.pushString("data");
							l.setField(-2, "status");
							//from https://stackoverflow.com/a/16094594
							int db = (int) ((20.0 * log10(recorder.getMaxAmplitude()) - 20.0 * log10(700))+20);
							if(db < 0){
								l.pushNumber(0);
							}else{
								l.pushNumber(db);
							}


							l.setField(-2, "powerLevel");
							try {
								CoronaLua.dispatchEvent(l,recLis, 0);
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					});
				}
			}

			loopGetAmplitude = new Timer();
			isRecording = true;
			loopGetAmplitude.schedule(new GetAmps(), 0, 100);

			return 0;
		}
	}
	private class stopRecording implements NamedJavaFunction {
		/**
		 * Gets the name of the Lua function as it would appear in the Lua script.
		 * @return Returns the name of the custom Lua function.
		 */
		@Override
		public String getName() {
			return "stopRecording";
		}

		/**
		 * This method is called when the Lua function is called.
		 * <p>
		 * Warning! This method is not called on the main UI thread.
		 * @param L Reference to the Lua state.
		 *                 Needed to retrieve the Lua function's parameters and to return values back to Lua.
		 * @return Returns the number of values to be returned by the Lua function.
		 */
		@Override
		public int invoke(LuaState L) {
			isRecording = false;
			recorder.stop();
			recDis.send(new CoronaRuntimeTask() {
				@Override
				public void executeUsing(CoronaRuntime coronaRuntime) {
					LuaState l = coronaRuntime.getLuaState();
					CoronaLua.newEvent(l, "amplitudeRecorder");
					l.pushString("stopped");
					l.setField(-2, "status");

					try {
						CoronaLua.dispatchEvent(l,recLis, 0);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
			loopGetAmplitude.cancel();
			loopGetAmplitude.purge();
			return 0;
		}
	}

}
