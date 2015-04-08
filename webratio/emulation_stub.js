function createStubs() {

	var EMULATOR_NOTIFICATION_DEVICE_ID = "private:emulator";

    function log() {
        var args = [].slice.call(arguments, 0);
        args.unshift("[push notifications]");
        console.log.apply(console, args);
    }

    return {
        PushPlugin: {
            register: function(options) {
				log("Registered to the messaging service");
				if (options.senderID) { // Android
					window[options.ecb]({
						event: "registered",
						regid: EMULATOR_NOTIFICATION_DEVICE_ID
					});
				} else { // iOS
					return EMULATOR_NOTIFICATION_DEVICE_ID;
				}
            },
            hasColdStartNotification: function() {
                // do nothing
                return false;
            },
            unregister: function(options) {
                log("Unregistered from the messaging service");
            },
            showToastNotification: function(options) {
                throw new Error("Toast notifications not supported on this platform");
            },
            setApplicationIconBadgeNumber: function(options) {
				log("Application badge set to", options.badge);
            }
        }
    };
};