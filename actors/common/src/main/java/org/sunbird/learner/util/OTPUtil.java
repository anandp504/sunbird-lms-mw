package org.sunbird.learner.util;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.KeyRepresentation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.actor.background.BackgroundOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.models.util.PropertiesCache;
import org.sunbird.common.request.Request;
import org.sunbird.notification.sms.provider.ISmsProvider;
import org.sunbird.notification.utils.SMSFactory;

public final class OTPUtil {

  private static final int MINIMUM_OTP_LENGTH = 6;
  private static final int SECONDS_IN_MINUTES = 60;

  private OTPUtil() {}

  public static String generateOTP() {
    String otpSize = PropertiesCache.getInstance().getProperty(JsonKey.SUNBIRD_OTP_LENGTH);
    int codeDigit = StringUtils.isBlank(otpSize) ? MINIMUM_OTP_LENGTH : Integer.valueOf(otpSize);
    GoogleAuthenticatorConfig config =
        new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
            .setCodeDigits(codeDigit)
            .setKeyRepresentation(KeyRepresentation.BASE64)
            .build();
    GoogleAuthenticator gAuth = new GoogleAuthenticator(config);
    GoogleAuthenticatorKey key = gAuth.createCredentials();
    String secret = key.getKey();
    int code = gAuth.getTotpPassword(secret);
    return String.valueOf(code);
  }

  public static void sendOTPSMS(Map<String, Object> otpMap) {
    if (StringUtils.isBlank((String) otpMap.get(JsonKey.PHONE))) {
      return;
    }
    Map<String, String> smsTemplate = new HashMap<>();
    smsTemplate.put(JsonKey.OTP, (String) otpMap.get(JsonKey.OTP));
    smsTemplate.put(
        JsonKey.OTP_EXPIRATION_IN_MINUTES, (String) otpMap.get(JsonKey.OTP_EXPIRATION_IN_MINUTES));
    String sms = ProjectUtil.getOTPSMSBody(smsTemplate);

    ProjectLogger.log("SMS text : " + sms, LoggerEnum.INFO);
    String countryCode = "";
    if (StringUtils.isBlank((String) otpMap.get(JsonKey.COUNTRY_CODE))) {
      countryCode = PropertiesCache.getInstance().getProperty(JsonKey.SUNBIRD_DEFAULT_COUNTRY_CODE);
    } else {
      countryCode = (String) otpMap.get(JsonKey.COUNTRY_CODE);
    }
    ISmsProvider smsProvider = SMSFactory.getInstance("91SMS");
    ProjectLogger.log(
        "SMS OTP text : " + sms + " with phone " + (String) otpMap.get(JsonKey.PHONE),
        LoggerEnum.INFO.name());
    boolean response = smsProvider.send((String) otpMap.get(JsonKey.PHONE), countryCode, sms);
    ProjectLogger.log("Response from smsProvider : " + response, LoggerEnum.INFO);
    if (response) {
      ProjectLogger.log(
          "OTP Message sent successfully to ." + (String) otpMap.get(JsonKey.PHONE),
          LoggerEnum.INFO.name());
    } else {
      ProjectLogger.log(
          "OTP Message failed for ." + (String) otpMap.get(JsonKey.PHONE), LoggerEnum.INFO.name());
    }
  }

  public static Request sendOTPMailRequest(Map<String, Object> emailTemplateMap) {
    Request request = null;
    if ((StringUtils.isBlank((String) emailTemplateMap.get(JsonKey.EMAIL)))) {
      return request;
    }
    String envName =
        PropertiesCache.getInstance().getProperty(JsonKey.SUNBIRD_INSTALLATION_DISPLAY_NAME);
    String welcomeSubject =
        PropertiesCache.getInstance().getProperty(JsonKey.ONBOARDING_MAIL_SUBJECT);
    emailTemplateMap.put(JsonKey.SUBJECT, ProjectUtil.formatMessage(welcomeSubject, envName));
    List<String> reciptientsMail = new ArrayList<>();
    reciptientsMail.add((String) emailTemplateMap.get(JsonKey.EMAIL));
    emailTemplateMap.put(JsonKey.RECIPIENT_EMAILS, reciptientsMail);
    emailTemplateMap.put(JsonKey.EMAIL_TEMPLATE_TYPE, JsonKey.OTP);
    request = new Request();
    request.setOperation(BackgroundOperations.emailService.name());
    request.put(JsonKey.EMAIL_REQUEST, emailTemplateMap);
    return request;
  }

  public static String getOTPExpirationInMinutes() {
    String expirationInSeconds =
        PropertiesCache.getInstance().getProperty(JsonKey.SUNBIRD_OTP_EXPIRATION);
    int otpExpiration = Integer.valueOf(expirationInSeconds);
    int otpExpirationInMinutes = Math.floorDiv(otpExpiration, SECONDS_IN_MINUTES);
    return String.valueOf(otpExpirationInMinutes);
  }
}
