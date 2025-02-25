//
//  PluginLibrary.mm
//  TemplateApp
//
//  Copyright (c) 2012 __MyCompanyName__. All rights reserved.
//

#import "amplitudeRecorder.h"

#include <CoronaRuntime.h>
#import <UIKit/UIKit.h>
#import <AVFoundation/AVFoundation.h>
#import <Accelerate/Accelerate.h>
#include <cmath>
// ----------------------------------------------------------------------------
static NSString *
ToNSString( lua_State *L, int index )
{
    NSString *result = nil;
    
    int t = lua_type( L, -2 );
    switch ( t )
    {
        case LUA_TNUMBER:
            result = [NSString stringWithFormat:@"%g", lua_tonumber( L, index )];
            break;
        default:
            result = [NSString stringWithUTF8String:lua_tostring( L, index )];
            break;
    }
    
    return result;
}
@interface  recorderDel : UIViewController  <AVAudioRecorderDelegate>
@property (nonatomic, assign) lua_State *luaStateAudio;
@end
static recorderDel * recorderDelHolder = [[recorderDel alloc] init];
static AVAudioRecorder * recorder;
static NSTimer *timer;
static CoronaLuaRef amplitudeRecorderLis;
class amplitudeRecorder
{
	public:
		typedef amplitudeRecorder Self;

	public:
		static const char kName[];
		static const char kEvent[];

	protected:
        amplitudeRecorder();

	public:
		bool Initialize( CoronaLuaRef listener );

	public:
		CoronaLuaRef GetListener() const { return fListener; }

	public:
		static int Open( lua_State *L );

	protected:
		static int Finalizer( lua_State *L );

	public:
		static Self *ToLibrary( lua_State *L );

	public:
        static int record( lua_State *L );
        static int stopRecording( lua_State *L );
    
    
    

	private:
		CoronaLuaRef fListener;
};

// ----------------------------------------------------------------------------

const char amplitudeRecorder::kName[] = "plugin.amplitudeRecorder";

// This corresponds to the event name, e.g. [Lua] event.name
const char amplitudeRecorder::kEvent[] = "pluginamplitudeRecorderevent";

amplitudeRecorder::amplitudeRecorder()
:	fListener( NULL )
{
}

bool
amplitudeRecorder::Initialize( CoronaLuaRef listener )
{
	// Can only initialize listener once
	bool result = ( NULL == fListener );

	if ( result )
	{
		fListener = listener;
	}

	return result;
}

int
amplitudeRecorder::Open( lua_State *L )
{
	// Register __gc callback
	const char kMetatableName[] = __FILE__; // Globally unique string to prevent collision
	CoronaLuaInitializeGCMetatable( L, kMetatableName, Finalizer );

	// Functions in library
	const luaL_Reg kVTable[] =
	{
		{ "record", record },
		{ "stopRecording", stopRecording },
        
        

		{ NULL, NULL }
	};

	// Set library as upvalue for each library function
	Self *library = new Self;
	CoronaLuaPushUserdata( L, library, kMetatableName );

	luaL_openlib( L, kName, kVTable, 1 ); // leave "library" on top of stack

	return 1;
}

int
amplitudeRecorder::Finalizer( lua_State *L )
{
	Self *library = (Self *)CoronaLuaToUserdata( L, 1 );

	CoronaLuaDeleteRef( L, library->GetListener() );

	delete library;

	return 0;
}

amplitudeRecorder *
amplitudeRecorder::ToLibrary( lua_State *L )
{
	// library is pushed as part of the closure
	Self *library = (Self *)CoronaLuaToUserdata( L, lua_upvalueindex( 1 ) );
	return library;
}
int
amplitudeRecorder::record( lua_State *L )
{
    NSURL *myUrl = [NSURL URLWithString:ToNSString(L, 1)];
    amplitudeRecorderLis = CoronaLuaNewRef(L, 2);
    NSError *error = nil;
    [[NSFileManager defaultManager] removeItemAtPath:myUrl.absoluteString error:&error];
    [[AVAudioSession sharedInstance] requestRecordPermission:^(BOOL granted) {
            if (granted) {
                
                recorderDelHolder.luaStateAudio = L;
                
                dispatch_async(dispatch_get_main_queue(), ^(void){
                
                    [[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryPlayAndRecord error:nil];
                    [[AVAudioSession sharedInstance] setActive:YES error:nil];
                    NSError *error;

                    // Recording settings
                    NSMutableDictionary *settings = [NSMutableDictionary dictionary];

                    [settings setValue:[NSNumber numberWithInt:kAudioFormatMPEG4AAC] forKey:AVFormatIDKey];
                    [settings setValue:[NSNumber numberWithFloat:44100.0] forKey:AVSampleRateKey];
                    [settings setValue:[NSNumber numberWithInt: 2] forKey:AVNumberOfChannelsKey];


                

                    // Create recorder
                    recorder = [[AVAudioRecorder alloc] initWithURL:myUrl settings:settings error:&error];
                    if (!recorder)
                    {
                        NSLog(@"Error establishing recorder: %@", error.localizedFailureReason);
                        
                    }

                    // Initialize degate, metering, etc.
                    recorder.delegate = recorderDelHolder;
                    recorder.meteringEnabled = YES;

                    if (![recorder prepareToRecord])
                    {
                        NSLog(@"Error: Prepare to record failed");
                        //[self say:@"Error while preparing recording"];
                        
                    }

                    if (![recorder record])
                    {
                        NSLog(@"Error: Record failed");
                        
                    }

                    //Set a timer to monitor levels, current time
                    timer = [NSTimer scheduledTimerWithTimeInterval:0.1f target:recorderDelHolder selector:@selector(timerTask) userInfo:nil repeats:YES];
                    
                    CoronaLuaNewEvent(L, "amplitudeRecorder");
                    lua_pushstring(L, "recording");
                    lua_setfield(L, -2, "status");
                    CoronaLuaDispatchEvent(L, amplitudeRecorderLis, 0);
                    [[NSNotificationCenter defaultCenter] addObserver:recorderDelHolder
                                                             selector:@selector(receiveSuspendNotification:)
                                                                 name:UIApplicationWillResignActiveNotification
                                                               object:nil];
                });
            }
            else {
                CoronaLuaNewEvent(L, "amplitudeRecorder");
                lua_pushstring(L, "permissionDenied");
                lua_setfield(L, -2, "status");
                CoronaLuaDispatchEvent(L, amplitudeRecorderLis, 0);
            }
        }];
   
    
    return 0;
}
int
amplitudeRecorder::stopRecording( lua_State *L )
{
    
    dispatch_async(dispatch_get_main_queue(), ^(void){
        [recorder stop];
        [timer invalidate];
        CoronaLuaNewEvent(recorderDelHolder.luaStateAudio, "amplitudeRecorder");
        lua_pushstring(recorderDelHolder.luaStateAudio, "stopped");
        lua_setfield(recorderDelHolder.luaStateAudio, -2, "status");
        
       
        CoronaLuaDispatchEvent(recorderDelHolder.luaStateAudio, amplitudeRecorderLis, 0);
        
    });

	return 0;
}
@implementation recorderDel
#warning Thread Safety
//------------------------------------------------------------------------------



// so you can update your interface accordingly.

- (void) receiveSuspendNotification:(NSNotification*)notif
{
    dispatch_async(dispatch_get_main_queue(), ^(void){
        [recorder stop];
        [timer invalidate];
        
    });
}
- (void) timerTask {
   [recorder updateMeters];
   float level = [recorder peakPowerForChannel:0];
    CoronaLuaNewEvent(recorderDelHolder.luaStateAudio, "amplitudeRecorder");
    lua_pushstring(recorderDelHolder.luaStateAudio, "data");
    lua_setfield(recorderDelHolder.luaStateAudio, -2, "status");
    lua_pushnumber(recorderDelHolder.luaStateAudio, pow(10.0, level / 20.0) * 100.0);
    lua_setfield(recorderDelHolder.luaStateAudio, -2, "powerLevel");
    CoronaLuaDispatchEvent(recorderDelHolder.luaStateAudio, amplitudeRecorderLis, 0);
}
@end


CORONA_EXPORT int luaopen_plugin_amplitudeRecorder( lua_State *L )
{
	return amplitudeRecorder::Open( L );
}
