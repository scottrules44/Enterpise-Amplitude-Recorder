local amplitudeRecorder = require "plugin.amplitudeRecorder"
local widget = require("widget")
local json =require("json")
local myRecPath = system.pathForFile("record.m4a",system.DocumentsDirectory)

local bg = display.newRect(display.contentCenterX, display.contentCenterY, display.actualContentWidth, display.actualContentHeight)
bg:setFillColor(0,.5,0)

local title = display.newText("Amplitude Recorder", display.contentCenterX, 50, native.systemFontBold, 14)

local recordAudio
recordAudio = widget.newButton({
    label = "Record Audio",
    x = display.contentCenterX,
    y = display.contentCenterY,
    labelColor = { default={ 1, 1, 1 }, over={ 0, 0, 0, 0.5 } },
    onRelease =  function ()
        amplitudeRecorder.record(myRecPath, function(e)
            print(json.encode(e))
        end)
    end
})
local function appPermissionsListener( event )

end
local options =
{
    appPermission = "Microphone",
    urgency = "Critical",
    listener = appPermissionsListener,
    rationaleTitle = "Microphone access required",
    rationaleDescription = "Microphone access is required to take record. Re-request now?",
    settingsRedirectTitle = "Alert",
    settingsRedirectDescription = "Without the ability to record, this app cannot properly function. Please grant record access within Settings."
}
native.showPopup( "requestAppPermission", options )

local playRecordedSound
local stopAudio
stopAudio = widget.newButton({
    label = "Stop Recording",
    x = display.contentCenterX,
    y = display.contentCenterY+50,
    labelColor = { default={ 1, 1, 1 }, over={ 0, 0, 0, 0.5 } },
    onRelease =  function ()
        amplitudeRecorder.stopRecording()
        playRecordedSound.alpha = 1
    end
})


playRecordedSound = widget.newButton({
    label = "Play Audio",
    x = display.contentCenterX,
    y = display.contentCenterY+100,
    labelColor = { default={ 1, 1, 1 }, over={ 0, 0, 0, 0.5 } },
    onRelease =  function ()
        media.playSound( "record.m4a",system.DocumentsDirectory )
    end
})
playRecordedSound.alpha = 0
