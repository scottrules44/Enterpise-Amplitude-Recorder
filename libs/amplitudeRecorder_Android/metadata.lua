local metadata =
{
	plugin =
	{
		format = 'jar',
		manifest =
		{
			permissions = {},
			usesPermissions =
			{
				"android.permission.RECORD_AUDIO",
				"android.permission.WRITE_EXTERNAL_STORAGE",
				"android.permission.READ_EXTERNAL_STORAGE",
			},
			usesFeatures = {},
			applicationChildElements =
			{

			},
		},
	},

}

return metadata
