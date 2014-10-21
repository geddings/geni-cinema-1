package net.floodlightcontroller.genicinema.web;

public class JsonStrings {
	/*
	 * Reusable strings for all message types.
	 */
	private static final String NAME = "name";
	private static final String DESCRIPTION = "description";
	private static final String VIEW_PASSWORD = "view_password";
	private static final String ADMIN_PASSWORD = "admin_password";
	private static final String CHANNEL_ID = "channel_id";
	private static final String CLIENT_ID = "client_id";
	private static final String GATEWAY_IP = "gateway_ip";
	private static final String GATEWAY_PORT = "gateway_port";
	private static final String GATEWAY_PROTOCOL = "gateway_protocol";
	private static final String RESULT = "result";
	private static final String RESULT_MESSAGE = "message";
	private static final String AGGREGATE_NAME = "aggregate";
	
	public static class Add {
		public static class Request {
			public static final String name = NAME;
			public static final String description = DESCRIPTION;
			public static final String view_password = VIEW_PASSWORD;
			public static final String admin_password = ADMIN_PASSWORD;	
		}

		public static class Response {
			public static final String name = Add.Request.name;
			public static final String description = Add.Request.description;
			public static final String view_password = Add.Request.view_password;
			public static final String admin_password = Add.Request.admin_password;
			public static final String channel_id = CHANNEL_ID;
			public static final String gateway_ip = GATEWAY_IP;
			public static final String gateway_port = GATEWAY_PORT;
			public static final String gateway_protocol = GATEWAY_PROTOCOL;
			public static final String result = RESULT; // success or failure
			public static final String result_message = RESULT_MESSAGE;
		}	
	}
	
	public static class Watch {
		public static class Request {
			public static final String client_id = CLIENT_ID;
			public static final String channel_id = CHANNEL_ID;
			public static final String view_password = VIEW_PASSWORD;
		}

		public static class Response {
			public static final String name = NAME;
			public static final String description = DESCRIPTION;
			public static final String channel_id = Watch.Request.channel_id;
			public static final String client_id = Watch.Request.client_id;
			public static final String gateway_ip = GATEWAY_IP;
			public static final String gateway_port = GATEWAY_PORT;
			public static final String gateway_protocol = GATEWAY_PROTOCOL;
			public static final String result = RESULT; // success or failure
			public static final String result_message = RESULT_MESSAGE;
		}	
	}

	public static class Remove {
		public static class Request {
			public static final String channel_id = CHANNEL_ID;
			public static final String admin_password = ADMIN_PASSWORD;
		}

		public static class Respond {
			public static final String result = RESULT; // success or failure
			public static final String result_message = RESULT_MESSAGE;
		}
	}

	public static class Modify {
		public static class Request {
			public static final String channel_id = CHANNEL_ID;
			public static final String name = NAME;
			public static final String description = DESCRIPTION;
			public static final String view_password = VIEW_PASSWORD;
			public static final String admin_password = ADMIN_PASSWORD;
		}

		public static class Respond {
			public static final String channel_id = Modify.Request.channel_id;
			public static final String name = Modify.Request.name;
			public static final String description = Modify.Request.description;
			public static final String view_password = Modify.Request.view_password;
			public static final String admin_password = Modify.Request.admin_password;
			public static final String gateway_ip = GATEWAY_IP;
			public static final String gateway_port = GATEWAY_PORT;
			public static final String gateway_protocol = GATEWAY_PROTOCOL;
			public static final String result = RESULT; // success or failure
			public static final String result_message = RESULT_MESSAGE;
		}
	}

	public static class Query {
		public static class Request {

		}
		
		public static class Response {
			public static final String name = NAME;
			public static final String aggregate_name = AGGREGATE_NAME;
			public static final String description = DESCRIPTION;
			public static final String channel_id = CHANNEL_ID;
			public static final String result = RESULT; // success or failure
			public static final String result_message = RESULT_MESSAGE;
		}
	}
}