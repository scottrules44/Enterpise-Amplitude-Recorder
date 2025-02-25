//
//  PluginLibrary.h
//  TemplateApp
//
//  Copyright (c) 2012 __MyCompanyName__. All rights reserved.
//

#ifndef _amplitudeRecorder_H__
#define _amplitudeRecorder_H__

#include <CoronaLua.h>
#include <CoronaMacros.h>

// This corresponds to the name of the library, e.g. [Lua] require "plugin.library"
// where the '.' is replaced with '_'
CORONA_EXPORT int luaopen_plugin_amplitudeRecorder( lua_State *L );

#endif // _PluginLibrary_H__
