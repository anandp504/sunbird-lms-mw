package org.sunbird.badge.service.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.sunbird.badge.model.BadgeClassExtension;
import org.sunbird.badge.service.BadgeClassExtensionService;
import org.sunbird.badge.service.BadgingService;
import org.sunbird.badge.util.BadgingUtil;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.HttpUtilResponse;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.BadgingJsonKey;
import org.sunbird.common.models.util.HttpUtil;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.models.util.PropertiesCache;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.util.CourseBatchSchedulerUtil;
import org.sunbird.learner.util.Util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 
 * @author Manzarul
 *
 */
public class BadgrServiceImpl implements BadgingService {
	BadgeClassExtensionService badgeClassExtensionService = new BadgeClassExtensionServiceImpl();
	private ObjectMapper mapper = new ObjectMapper();
	private static CassandraOperation cassandraOperation = ServiceFactory.getInstance();
	private static final String ASSERTION_DUMMY_DOMAIN = "@gmail.com";
	public BadgrServiceImpl() {
		this.badgeClassExtensionService = new BadgeClassExtensionServiceImpl();
	}
	public static Map<String, String> headerMap = new HashMap<>();
	static {
		String header = System.getenv(JsonKey.EKSTEP_AUTHORIZATION);
		if (ProjectUtil.isStringNullOREmpty(header)) {
			header = PropertiesCache.getInstance().getProperty(JsonKey.EKSTEP_AUTHORIZATION);
		} else {
			header = JsonKey.BEARER + header;
		}
		headerMap.put(JsonKey.AUTHORIZATION, header);
		headerMap.put("Content-Type", "application/json");
	}
	public BadgrServiceImpl(BadgeClassExtensionService badgeClassExtensionService) {
		this.badgeClassExtensionService = badgeClassExtensionService;
	}

	@Override
	public Response createIssuer(Request request) throws IOException {
		// TODO Auto-generated method stub
		Map<String, Object> req = request.getRequest();
		byte[] image = null;
		Map<String, byte[]> fileData = new HashMap<>();
		if (req.containsKey(JsonKey.IMAGE) && null != req.get(JsonKey.IMAGE)) {
			image = (byte[]) req.get(JsonKey.IMAGE);
		}
		Map<String, String> requestData = new HashMap<>();
		requestData.put(JsonKey.NAME, (String) req.get(JsonKey.NAME));
		requestData.put(JsonKey.DESCRIPTION, (String) req.get(JsonKey.DESCRIPTION));
		requestData.put(JsonKey.URL, (String) req.get(JsonKey.URL));
		requestData.put(JsonKey.EMAIL, (String) req.get(JsonKey.EMAIL));

		String url = "/v1/issuer/issuers";
		Map<String, String> headers = BadgingUtil.getBadgrHeaders();
		// since the content type here is not application/json so removing this key.
		headers.remove("Content-Type");
		if (null != image) {
			fileData.put(JsonKey.IMAGE, image);
		}
		HttpUtilResponse httpResponse = HttpUtil
				.postFormData(requestData, fileData, headers, BadgingUtil.getBadgrBaseUrl() + url);

		BadgingUtil.throwBadgeClassExceptionOnErrorStatus(httpResponse.getStatusCode() ,null );
		Response response = new Response();
		BadgingUtil.prepareBadgeIssuerResponse(httpResponse.getBody(), response.getResult());

		return response;
	}

	@Override
	public Response getIssuerDetails(Request request) throws IOException {
		// TODO Auto-generated method stub
		Map<String, Object> req = request.getRequest();
		String slug = (String) req.get(JsonKey.SLUG);
		String url = "/v1/issuer/issuers" + "/" + slug;
		Map<String, String> headers = BadgingUtil.getBadgrHeaders();

		HttpUtilResponse httpResponse =HttpUtil.doGetRequest(BadgingUtil.getBadgrBaseUrl() + url, headers);
		BadgingUtil.throwBadgeClassExceptionOnErrorStatus(httpResponse.getStatusCode() ,null );
		Response response = new Response();
		BadgingUtil.prepareBadgeIssuerResponse(httpResponse.getBody(), response.getResult());
		return response;

	}

	@Override
	public Response getIssuerList(Request request) throws IOException {
		// TODO Auto-generated method stub
		String url = "/v1/issuer/issuers";
		Map<String, String> headers = BadgingUtil.getBadgrHeaders();
		HttpUtilResponse httpResponse =HttpUtil.doGetRequest(BadgingUtil.getBadgrBaseUrl() + url, headers);
		BadgingUtil.throwBadgeClassExceptionOnErrorStatus(httpResponse.getStatusCode() ,null );
		Response response = new Response();

		ObjectMapper mapper = new ObjectMapper();
		List<Map<String, Object>> issuers = new ArrayList<>();
		List<Map<String, Object>> data =
				mapper.readValue(httpResponse.getBody(), new TypeReference<List<Map<String, Object>>>() {});
		for (Object badge : data) {
			Map<String, Object> mappedBadge = new HashMap<>();
			BadgingUtil.prepareBadgeIssuerResponse((Map<String, Object>) badge, mappedBadge);
			issuers.add(mappedBadge);
		}
		Map<String, Object> res = new HashMap<>();
		res.put(BadgingJsonKey.ISSUERS, issuers);
		response.getResult().putAll(res);
		return response;
	}

	@Override
	public Response removeIssuer(Request request) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Response createBadgeClass(Request request) throws ProjectCommonException {
		Response response = new Response();

		try {
			Map<String, Object> requestData = request.getRequest();

			Map<String, String> formParams = (Map<String, String>) requestData.get(JsonKey.FORM_PARAMS);
			Map<String, byte[]> fileParams = (Map<String, byte[]>) requestData.get(JsonKey.FILE_PARAMS);

			String issuerId = formParams.remove(BadgingJsonKey.ISSUER_ID);
			String rootOrgId = formParams.remove(JsonKey.ROOT_ORG_ID);
			String type = formParams.remove(JsonKey.TYPE);
			String roles = formParams.remove(JsonKey.ROLES);
			List<String> rolesList = new ArrayList<>();
			if (roles != null) {
				ObjectMapper mapper = new ObjectMapper();
				rolesList = mapper.readValue(roles, ArrayList.class);
			}

			String subtype = "";
			if (formParams.containsKey(JsonKey.SUBTYPE)) {
				subtype = formParams.remove(JsonKey.SUBTYPE);
			}

			if (type != null) {
				type = type.toLowerCase();
			}

			if (subtype != null) {
				subtype = subtype.toLowerCase();
			}

			Map<String, String> headers = BadgingUtil.getBadgrHeaders(false);

			HttpUtilResponse httpUtilResponse = HttpUtil.postFormData(formParams, fileParams, headers, BadgingUtil.getBadgeClassUrl(issuerId));
			String badgrResponseStr = httpUtilResponse.getBody();

			BadgingUtil.throwBadgeClassExceptionOnErrorStatus(httpUtilResponse.getStatusCode(), badgrResponseStr);

			ObjectMapper mapper = new ObjectMapper();
			Map<String, Object> badgrResponseMap = (Map<String, Object>) mapper.readValue(badgrResponseStr, HashMap.class);
			String badgeId = (String) badgrResponseMap.get(BadgingJsonKey.SLUG);

			BadgeClassExtension badgeClassExt = new BadgeClassExtension(badgeId, issuerId, rootOrgId, type, subtype, rolesList);
			badgeClassExtensionService.save(badgeClassExt);

			BadgingUtil.prepareBadgeClassResponse(badgrResponseStr, badgeClassExt, response.getResult());
		} catch (IOException e) {
			BadgingUtil.throwBadgeClassExceptionOnErrorStatus(ResponseCode.SERVER_ERROR.getResponseCode(), e.getMessage());
		}

		return response;
	}

	@Override
	public Response getBadgeClassDetails(Request request) throws ProjectCommonException {
		Response response = new Response();

		try {
			Map<String, Object> requestData = request.getRequest();

			String issuerId = (String) requestData.get(BadgingJsonKey.ISSUER_ID);
			String badgeId = (String) requestData.get(BadgingJsonKey.BADGE_ID);

			Map<String, String> headers = BadgingUtil.getBadgrHeaders();
			String badgrUrl = BadgingUtil.getBadgeClassUrl(issuerId, badgeId);

			HttpUtilResponse httpUtilResponse = HttpUtil.doGetRequest(badgrUrl, headers);
			String badgrResponseStr = httpUtilResponse.getBody();

			BadgingUtil.throwBadgeClassExceptionOnErrorStatus(httpUtilResponse.getStatusCode(), badgrResponseStr);

			BadgeClassExtension badgeClassExtension = badgeClassExtensionService.get(badgeId);

			BadgingUtil.prepareBadgeClassResponse(badgrResponseStr, badgeClassExtension, response.getResult());
		} catch (IOException e) {
			BadgingUtil.throwBadgeClassExceptionOnErrorStatus(ResponseCode.SERVER_ERROR.getResponseCode(), e.getMessage());
		}

		return response;
	}

	@Override
	public Response getBadgeClassList(Request request) throws ProjectCommonException {
		Response response = new Response();

		Map<String, Object> requestData = request.getRequest();
		List<String> issuerList = (List<String>) requestData.get(BadgingJsonKey.ISSUER_LIST);
		Map<String, Object> context = (Map<String, Object>) requestData.get(BadgingJsonKey.CONTEXT);
		String rootOrgId = (String) context.get(JsonKey.ROOT_ORG_ID);
		String type = (String) context.get(JsonKey.TYPE);
		String subtype = (String) context.get(JsonKey.SUBTYPE);
		List<String> allowedRoles = (List<String>) context.get(JsonKey.ROLES);

		if (type != null) {
			type = type.toLowerCase();
		}

		if (subtype != null) {
			subtype = subtype.toLowerCase();
		}

		List<BadgeClassExtension> badgeClassExtList = badgeClassExtensionService.get(issuerList,
				rootOrgId, type, subtype, allowedRoles);
		List<String> filteredIssuerList = badgeClassExtList.stream()
				.map(badge -> badge.getIssuerId()).distinct().collect(Collectors.toList());

		List<Object> badges = new ArrayList<>();

		for (String issuerSlug : filteredIssuerList) {
			badges.addAll(listBadgeClassForIssuer(issuerSlug, badgeClassExtList));
		}

		response.put(BadgingJsonKey.BADGES, badges);

		return response;
	}

	private List<Object> listBadgeClassForIssuer(String issuerSlug, List<BadgeClassExtension> badgeClassExtensionList) throws ProjectCommonException {
		List<Object> filteredBadges = new ArrayList<>();

		try {
			Map<String, String> headers = BadgingUtil.getBadgrHeaders();
			String badgrUrl = BadgingUtil.getBadgeClassUrl(issuerSlug);

			HttpUtilResponse httpUtilResponse = HttpUtil.doGetRequest(badgrUrl, headers);
			String badgrResponseStr = httpUtilResponse.getBody();

			BadgingUtil.throwBadgeClassExceptionOnErrorStatus(httpUtilResponse.getStatusCode(), badgrResponseStr);

			ObjectMapper mapper = new ObjectMapper();
			List<Map<String, Object>> badges = mapper.readValue(badgrResponseStr, ArrayList.class);

			for (Map<String, Object> badge : badges) {
				BadgeClassExtension matchedBadgeClassExt = badgeClassExtensionList.stream()
						.filter(x -> x.getBadgeId().equals(badge.get(BadgingJsonKey.SLUG))).findFirst()
						.orElse(null);

				if (matchedBadgeClassExt != null) {
					Map<String, Object> mappedBadge = new HashMap<>();
					BadgingUtil.prepareBadgeClassResponse(badge, matchedBadgeClassExt, mappedBadge);
					filteredBadges.add(mappedBadge);
				}
			}
		} catch (IOException e) {
			BadgingUtil.throwBadgeClassExceptionOnErrorStatus(ResponseCode.SERVER_ERROR.getResponseCode(), e.getMessage());
		}

		return filteredBadges;
	}

	@Override
	public Response removeBadgeClass(Request requestMsg) throws ProjectCommonException {
		Response response = new Response();

		try {
			Map<String, Object> requestData = requestMsg.getRequest();

			String issuerId = (String) requestData.get(BadgingJsonKey.ISSUER_ID);
			String badgeId = (String) requestData.get(BadgingJsonKey.BADGE_ID);

			Map<String, String> headers = BadgingUtil.getBadgrHeaders();
			String badgrUrl = BadgingUtil.getBadgeClassUrl(issuerId, badgeId);

			HttpUtilResponse httpUtilResponse = HttpUtil.sendDeleteRequest(headers, badgrUrl);
			String badgrResponseStr = httpUtilResponse.getBody();

			BadgingUtil.throwBadgeClassExceptionOnErrorStatus(httpUtilResponse.getStatusCode(), badgrResponseStr);

			badgeClassExtensionService.delete(badgeId);
			response.put(JsonKey.MESSAGE, badgrResponseStr.replaceAll("^\"|\"$", ""));
		} catch (IOException e) {
			BadgingUtil.throwBadgeClassExceptionOnErrorStatus(ResponseCode.SERVER_ERROR.getResponseCode(), e.getMessage());
		}

		return response;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Response badgeAssertion(Request request) throws IOException {
		// Based on incoming recipientType and recipientId collect the email.
		Map<String, Object> requestedData = request.getRequest();
		String email = getEmail((String) requestedData.get(BadgingJsonKey.RECIPIENT_ID),
				(String) requestedData.get(BadgingJsonKey.RECIPIENT_TYPE));
		requestedData.put(BadgingJsonKey.RECIPIENT_EMAIL, email);
		String requestBody = BadgingUtil.createAssertionReqData(requestedData);
		String url = BadgingUtil.createBadgerUrl(requestedData, BadgingUtil.SUNBIRD_BADGER_CREATE_ASSERTION_URL, 2);
		ProjectLogger.log("AssertionData==" + requestBody + "  " + url + "  " + BadgingUtil.getBadgrHeaders(),
				LoggerEnum.INFO.name());
		HttpUtilResponse httpResponse = HttpUtil.doPostRequest(url, requestBody, BadgingUtil.getBadgrHeaders());
		ProjectLogger.log("AssertionDataResponse==" + httpResponse.getStatusCode(), LoggerEnum.INFO.name());
		BadgingUtil.throwBadgeClassExceptionOnErrorStatus(httpResponse.getStatusCode(), null);
		Map<String, Object> res = mapper.readValue(httpResponse.getBody(), HashMap.class);
		// calling to create response as per sunbird
		res = BadgingUtil.prepareAssertionResponse(res, new HashMap<String, Object>());
		Response response = new Response();
		response.getResult().putAll(res);
		return response;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Response getAssertionDetails(Request request) throws IOException {
		String url = BadgingUtil.createBadgerUrl(request.getRequest(), BadgingUtil.SUNBIRD_BADGER_GETASSERTION_URL, 3);
		HttpUtilResponse httpResponse = HttpUtil.doGetRequest(url, BadgingUtil.getBadgrHeaders());
		BadgingUtil.throwBadgeClassExceptionOnErrorStatus(httpResponse.getStatusCode(),null);
		Map<String, Object> res = mapper.readValue(httpResponse.getBody(), HashMap.class);
		// calling to create response as per sunbird
		res = BadgingUtil.prepareAssertionResponse(res, new HashMap<String, Object>());
		Response response = new Response();
		response.getResult().putAll(res);
		return response;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Response getAssertionList(Request request) throws IOException {
		Map<String, Object> filterMap = (Map<String, Object>) request.getRequest().get(JsonKey.FILTERS);
		List<String> requestData = (List) filterMap.get(BadgingJsonKey.ASSERTIONS);
		List<Map<String, Object>> responseList = new ArrayList<>();
		for (String assertionId : requestData) {
			WeakHashMap<String, Object> map = new WeakHashMap<>();
			map.put(BadgingJsonKey.ASSERTION_ID, assertionId);
			String url = BadgingUtil.createBadgerUrl(map, BadgingUtil.SUNBIRD_BADGER_GETASSERTION_URL, 3);
			HttpUtilResponse httpResponse = HttpUtil.doGetRequest(url, BadgingUtil.getBadgrHeaders());
			if (httpResponse.getStatusCode() == 200) {
				Map<String, Object> res = mapper.readValue(httpResponse.getBody(), HashMap.class);
				// calling to create response as per sunbird
				res = BadgingUtil.prepareAssertionResponse(res, new HashMap<String, Object>());
				responseList.add(res);
			}
		}
		if (responseList.size() == 0) {
			BadgingUtil.throwBadgeClassExceptionOnErrorStatus(ResponseCode.RESOURCE_NOT_FOUND.getResponseCode(), null);
		}
		Response response = new Response();
		response.getResult().put(BadgingJsonKey.ASSERTIONS, responseList);
		return response;
	}

	@Override
	public Response revokeAssertion(Request request) throws IOException {
		String url = BadgingUtil.createBadgerUrl(request.getRequest(), BadgingUtil.SUNBIRD_BADGER_GETASSERTION_URL, 3);
		String requestBody = BadgingUtil.createAssertionRevokeData(request.getRequest());
		HttpUtilResponse httpResponse = HttpUtil.sendDeleteRequest(requestBody,  BadgingUtil.getBadgrHeaders(), url);
		BadgingUtil.throwBadgeClassExceptionOnErrorStatus(httpResponse.getStatusCode(),null);
		Response response = new Response();
		response.getResult().put(JsonKey.STATUS, JsonKey.SUCCESS);
		return response;
	}

	@Override
	public Response deleteIssuer(Request request) throws IOException{
		Map<String, Object> req = request.getRequest();
		String slug = (String) req.get(JsonKey.SLUG);
		String url = "/v1/issuer/issuers" + "/" + slug;
		Map<String, String> headers = BadgingUtil.getBadgrHeaders();
		HttpUtilResponse httpResponse =HttpUtil.sendDeleteRequest(headers , BadgingUtil.getBadgrBaseUrl() + url);
		BadgingUtil.throwBadgeClassExceptionOnErrorStatus(httpResponse.getStatusCode() ,null );
		Response response = new Response();
		// since the response from badger service contains " at beging and end so remove that from response string
		response.put(JsonKey.MESSAGE , StringUtils.strip( httpResponse.getBody(), "\""));
		return response;
	}
	
	/**
	 * This method wu
	 * @param recipientId
	 * @param recipientType
	 * @return
	 */
	public static String getEmail(String recipientId, String recipientType) {
		System.out.println("collecting recipient id and recipientType " + recipientId +" " + recipientType);
		if (BadgingJsonKey.BADGE_TYPE_USER.equalsIgnoreCase(recipientType)) {
			return getUserEmailFromDB(recipientId);
		} else {
			return verifyContent(recipientId);
		}
	}
	
	/**
	 * This method will provide user email id.
	 * @param userId String
	 * @return String
	 */
	private static String getUserEmailFromDB(String userId) {
		Util.DbInfo usrDbInfo = Util.dbInfoMap.get(JsonKey.USER_DB);
		Response response = cassandraOperation.getRecordById(usrDbInfo.getKeySpace(), usrDbInfo.getTableName(), userId);
		List<Map<String, Object>> user = (List<Map<String, Object>>) response.get(JsonKey.RESPONSE);
		if ((user == null) || user.isEmpty()) {
			BadgingUtil.throwBadgeClassExceptionOnErrorStatus(ResponseCode.RESOURCE_NOT_FOUND.getResponseCode(),
					"User not found.");
		}
		String email = org.sunbird.common.models.util.datasecurity.impl.ServiceFactory
				.getDecryptionServiceInstance(null).decryptData((String) user.get(0).get(JsonKey.EMAIL));
		// verify the email format.
		if (ProjectUtil.isEmailvalid(email)) {
			return email;
		} else {
			String adminEmail = System.getenv(BadgingJsonKey.SUNBIRD_INSTALLATION_EMAIL);
			if (ProjectUtil.isStringNullOREmpty(adminEmail)) {
				return userId + ASSERTION_DUMMY_DOMAIN;
			}
			return adminEmail;
		}
	}
	
	/**
	 * This method will take contentId and make EKstep api call to do the content validation,
	 * if content found inside Ekstep then it will take createdBy attribute and make sunbird 
	 * api call to get the user email. if content not found then user will get 404 error. 
	 * @param contentId String
	 * @return String
	 */
	private static String verifyContent(String contentId) {
		String ekStepBaseUrl = System.getenv(JsonKey.EKSTEP_BASE_URL);
		String url = ekStepBaseUrl + PropertiesCache.getInstance().getProperty("sunbird_content_read") + "/"
				+ contentId;
		try {
			HttpUtilResponse response = HttpUtil.doGetRequest(url, CourseBatchSchedulerUtil.headerMap);
			if (response == null || response.getStatusCode() >= 300) {
				BadgingUtil.throwBadgeClassExceptionOnErrorStatus(ResponseCode.RESOURCE_NOT_FOUND.getResponseCode(),
						"Content not found.");
			}
			JSONObject object = new JSONObject(response.getBody());
			JSONObject data = object.getJSONObject(JsonKey.RESULT);
			JSONObject contentData = data.getJSONObject(JsonKey.CONTENT);
			String userId = contentData.getString(JsonKey.CREATED_BY);
			return getUserEmailFromDB(userId);
		} catch (IOException | JSONException e) {
			ProjectLogger.log(e.getMessage(), e);
			BadgingUtil.throwBadgeClassExceptionOnErrorStatus(ResponseCode.RESOURCE_NOT_FOUND.getResponseCode(),
					"Content not found.");
		}
		return contentId + ASSERTION_DUMMY_DOMAIN;
	}

	
}