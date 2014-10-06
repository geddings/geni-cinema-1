package net.floodlightcontroller.genicinema.web;

public class JsonStrings {
	public static class Add {
		public static class Request {
			public static final String name = "name";
			public static final String description = "description";
			public static final String view_password = "view-password";
			public static final String admin_password = "admin-password";	
		}

		public static class Response {
			public static final String name = Add.Request.name;
			public static final String description = Add.Request.description;
			public static final String view_password = Add.Request.view_password;
			public static final String admin_password = Add.Request.admin_password;
			public static final String id = "id";
			public static final String gateway_ip = "gateway-ip";
			public static final String gateway_port = "gateway-port";
			public static final String gateway_protocol = "gateway-protocol";
		}	
	}

	public static class Remove {
		public static class Request {
			public static final String name = "name";
			public static final String id = "id";
			public static final String admin_password = "admin-password";
		}

		public static class Respond {
			public static final String success = "success";
		}
	}

	public static class Modify {
		public static class Request {
			public static final String id = "id";
			public static final String name = "name";
			public static final String description = "description";
			public static final String view_password = "view-password";
			public static final String admin_password = "admin-password";
		}

		public static class Respond {
			public static final String id = Modify.Request.id;
			public static final String name = Modify.Request.name;
			public static final String description = Modify.Request.description;
			public static final String view_password = Modify.Request.view_password;
			public static final String admin_password = Modify.Request.admin_password;
			public static final String gateway_ip = "gateway-ip";
			public static final String gateway_port = "gateway-port";
			public static final String gateway_protocol = "gateway-protocol";
		}
	}

	public static class Query {
		public static class Request {
			public static final String name = "name";
			public static final String description = "description";
		}
		
		public static class Respond {
			public static final String name = Query.Request.name;
			public static final String description = Query.Request.description;
		}
	}
}