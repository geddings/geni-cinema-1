package net.floodlightcontroller.genicinema.web;

public class JsonStrings {
	private JsonStrings() {}; // prevent instantiation for this and all inner classes
	
	/*
	 * Reusable strings for all message types.
	 */
	private static final String NAME = "name";
	private static final String DESCRIPTION = "description";
	private static final String VIEW_PASSWORD = "view_password";
	private static final String ADMIN_PASSWORD = "admin_password";
	private static final String ADMIN_PASSWORD_NEW = "new_admin_password";
	private static final String CHANNEL_ID = "channel_id";
	private static final String CLIENT_ID = "client_id";
	private static final String GATEWAY_IP = "gateway_ip";
	private static final String GATEWAY_PORT = "gateway_port";
	private static final String GATEWAY_PROTOCOL = "gateway_protocol";
	private static final String RESULT = "result";
	private static final String RESULT_MESSAGE = "message";
	private static final String AGGREGATE_NAME = "aggregate";
	private static final String INGRESS_GWS = "ingress_gateways";
	private static final String EGRESS_GWS = "egress_gateways";
	
	public static class Result {
		private Result() {};
		
		public static final String result_code = RESULT;
		public static final String result_message = RESULT_MESSAGE;
		
		public static class Complete {
			private Complete() {};
			public static final String code = "0";
			public static final String message = "Request completed successfully. Please see JSON response for relevant information.";
		}
		public static class NoChannelsAvailable {
			private NoChannelsAvailable() {};
			public static final String code = "1";
			public static final String message = "There are no Channels available at this time. Please check back later.";
		}
		public static class IncorrectJsonFields {
			private IncorrectJsonFields() {};
			public static final String code = "2";
			public static final String message = "Did not receive all expected JSON fields in request.";
		}
		public static class ClientIdParseError {
			private ClientIdParseError() {};
			public static final String code = "3";
			public static final String message = "A client ID was provided, but it could not be parsed. Please check your client ID and try again.";
		}
		public static class ClientIdNotFound {
			private ClientIdNotFound() {};
			public static final String code = "4";
			public static final String message = "The specified client ID cannot be found. Please check your client ID and try again.";
		}
		public static class EgressGatewayNotFound {
			private EgressGatewayNotFound() {};
			public static final String code = "5";
			public static final String message = "Could not lookup egress Gateway from VLCStreamServer. Is GC in an inconsistent state?";
		}
		public static class ClientIpAllZeros {
			private ClientIpAllZeros() {};
			public static final String code = "6";
			public static final String message = "Client IP was detected as 0.0.0.0 (should never happen). Is there an issue with the HTTP client?";
		}
		public static class ClientIpParseError {
			private ClientIpParseError() {};
			public static final String code = "7";
			public static final String message = "A client IP could not be parsed from the HTTP header. That's odd.";
		}
		public static class IngressGatewayUnavailable {
			private IngressGatewayUnavailable() {};
			public static final String code = "8";
			public static final String message = "GENI Cinema is experiencing high load. Could not find an available GENI Cinema Ingress Gateway.";
		}
		public static class IngressVLCStreamServerUnavailable {
			private IngressVLCStreamServerUnavailable() {};
			public static final String code = "9";
			public static final String message = "GENI Cinema is experiencing high load. Could not allocate a new ingress stream to the GENI Cinema Service.";
		}
		public static class ChannelAddError {
			private ChannelAddError() {};
			public static final String code = "10";
			public static final String message = "Could not add the allocated channel to the GENI Cinema Service. This should never happen.";
		}
		public static class ChannelIdParseError {
			private ChannelIdParseError() {};
			public static final String code = "11";
			public static final String message = "Could not parse specified Channel ID. Please provide a positive, integer Channel ID.";
		}
		public static class ChannelIdUnavailable {
			private ChannelIdUnavailable() {};
			public static final String code = "12";
			public static final String message = "The Channel ID specified is not available as this time.";
		}
		public static class AdminPasswordIncorrect {
			private AdminPasswordIncorrect() {};
			public static final String code = "13";
			public static final String message = "The admin password entered does not match that of the Channel ID specified.";
		}
		public static class ViewPasswordIncorrect {
			private ViewPasswordIncorrect() {};
			public static final String code = "14";
			public static final String message = "The view password entered does not match that of the Channel ID specified.";
		}
		public static class EgressGatewayUnavailable {
			private EgressGatewayUnavailable() {};
			public static final String code = "15";
			public static final String message = "GENI Cinema is experiencing high load. Could not find an available GENI Cinema Egress Gateway.";
		}
		public static class EgressVLCStreamServerUnavailable {
			private EgressVLCStreamServerUnavailable() {};
			public static final String code = "16";
			public static final String message = "GENI Cinema is experiencing high load. Could not allocate a new egress stream to the GENI Cinema Service.";
		}
		public static class SwitchesNotReady {
			private SwitchesNotReady() {};
			public static final String code = "99";
			public static final String message = "All switches are not connected to the GENI Cinema Service. Please try again or contact kwang@clemson.edu for assistance (jk).";
		}
	}
	
	public static class Add {
		private Add() {};
		
		public static class Request {
			private Request() {};
			public static final String name = NAME;
			public static final String description = DESCRIPTION;
			public static final String view_password = VIEW_PASSWORD;
			public static final String admin_password = ADMIN_PASSWORD;	
		}

		public static class Response {
			private Response() {};
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
		private Watch() {};
		public static class Request {
			private Request() {};
			public static final String client_id = CLIENT_ID;
			public static final String channel_id = CHANNEL_ID;
			public static final String view_password = VIEW_PASSWORD;
		}

		public static class Response {
			private Response() {};
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
	
	public static class Disconnect {
		private Disconnect() {};
		public static class Request {
			private Request() {};
			public static final String client_id = CLIENT_ID;
		}

		public static class Response {
			private Response() {};
			public static final String result = RESULT; // success or failure
			public static final String result_message = RESULT_MESSAGE;
		}	
	}

	public static class Remove {
		private Remove() {};
		public static class Request {
			private Request() {};
			public static final String channel_id = CHANNEL_ID;
			public static final String admin_password = ADMIN_PASSWORD;
		}

		public static class Response {
			private Response() {};
			public static final String result = RESULT; // success or failure
			public static final String result_message = RESULT_MESSAGE;
		}
	}

	public static class Modify {
		private Modify() {};
		public static class Request {
			private Request() {};
			public static final String channel_id = CHANNEL_ID;
			public static final String name = NAME;
			public static final String description = DESCRIPTION;
			public static final String view_password = VIEW_PASSWORD;
			public static final String admin_password = ADMIN_PASSWORD;
			public static final String admin_password_new = ADMIN_PASSWORD_NEW;
		}

		public static class Response {
			private Response() {};
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
		private Query() {};
		public static class Request {
			private Request() {};
		}
		
		public static class Response {
			private Response() {};
			public static final String name = NAME;
			public static final String aggregate_name = AGGREGATE_NAME;
			public static final String description = DESCRIPTION;
			public static final String channel_id = CHANNEL_ID;
			public static final String result = RESULT; // success or failure
			public static final String result_message = RESULT_MESSAGE;
		}
	}
	
	public static class GatewayInfo {
		private GatewayInfo() {};
		public static class Request {
			private Request() {};
		}
		
		public static class Response {
			private Response() {};
			public static final String ingress_gateways = INGRESS_GWS;
			public static final String egress_gateways = EGRESS_GWS;
		}
	}
}