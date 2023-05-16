package com.atc.openauction.ui.spring.myaccount;

import com.atc.openauction.ApplicationThread;
import com.atc.openauction.Global;
import com.atc.openauction.dao.myaccount.UserPreferenceConstants.USER_PREFERENCE_NAME;
import com.atc.openauction.security.Preference;
import com.atc.openauction.security.PrivateLabel;
import com.atc.openauction.security.UserLoginInfo;
import com.atc.openauction.service.CacheService;
import com.atc.openauction.service.myaccount.DefaultCheckoutInfo;
import com.atc.openauction.service.myaccount.NewWaveCommand;
import com.atc.openauction.service.myaccount.QuickBidCommand;
import com.atc.openauction.ui.RequestSupport;
import com.atc.openauction.ui.ServletContextSupport;
import com.atc.openauction.ui.SessionSupport;
import com.atc.openauction.ui.searchversion.SearchVersionSupportI;
import com.atc.openauction.ui.spring.annotatedcontrollers.myaccount.SupportedPreferenceConfig;
import com.atc.openauction.util.KeyValuePair;
import com.atc.openauction.util.KeyValuePairGen;
import com.atc.openauction.util.Util;
import com.atc.openauction.vehicle.Service;
import com.openlane.util.esapi.HTTPUtilities2;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.owasp.esapi.ESAPI;
import org.owasp.esapi.errors.IntrusionException;
import org.owasp.esapi.errors.ValidationException;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;
import org.springframework.web.util.WebUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MyAccountControllerSitePref extends MyAccountControllerBase {
	private final Log logger = LogFactory.getLog(MyAccountControllerSitePref.class);
	private static final String MY_ACCOUNT_COMMAND = "myAccountCommandSitePref";
	protected SessionSupport sessionSupport = new SessionSupport();
	protected ServletContextSupport servletContextSupport = new ServletContextSupport();
	private SearchVersionSupportI searchVersionSupport;
	private final String LOCAL_TIME_ZONE = "LOCAL_TIME_ZONE";
	public static final String MY_FAV_AUC_SEARCH_PREFIX = "amg.search.engine.url.prefix";
	public static final String TRANSACTIONS_SEARCHER_REAL10 = "api/rest/1.0-transaction/";
	private CacheService cacheService;


	public void setSearchVersionSupport(SearchVersionSupportI searchVersionSupport) {
		this.searchVersionSupport = searchVersionSupport;
	}
	
	public MyAccountControllerSitePref() {
		setCommandClass(MyAccountCommandSitePref.class);
		setCommandName(MY_ACCOUNT_COMMAND);
		setValidator(new MyAccountSitePrefValidator());
	}


	@SuppressWarnings("serial")
	private class SessionTimeoutException extends Exception {
	}

	private DefaultCheckoutInfo getDefaultCheckoutInfo(HttpServletRequest request) throws SessionTimeoutException {
		Long userId = getUserId(request);
		Long plId = getPrivateLabelId(request);
		Set<String> supportedPreferences = SupportedPreferenceConfig.getSuportedPreferences(plId);
		Locale locale=RequestSupport.getRequestPrivateLabelLocale(request);
		if(!(boolean)request.getAttribute("isPlFrSupported"))
			locale=locale.ENGLISH;
		DefaultCheckoutInfo defaultCheckoutInfo = getMyAccountService().getDefaultCheckoutInfo(userId, plId, null, supportedPreferences,locale);
		return defaultCheckoutInfo;
	}
	
	public CacheService getCacheService() {
		return cacheService;
	}

	public void setCacheService(CacheService cacheService) {
		this.cacheService = cacheService;
	}

	private Long getUserId(HttpServletRequest request)
			throws SessionTimeoutException {
		UserLoginInfo userLoginInfo = sessionSupport
				.getAuthenticatedUser(request);
		if (userLoginInfo == null)
			throw new SessionTimeoutException();
		Long userId = userLoginInfo.getUserId();
		return userId;
	}

	protected PrivateLabel getPrivateLabel(HttpServletRequest request){
		return ApplicationThread.getPrivateLabelInThreadLocal();
	}
	
	protected Long getPrivateLabelId(HttpServletRequest request){
		return getPrivateLabel(request).getId();
	}

	public void submitSitePrefCheckout(HttpServletRequest request, MyAccountCommandSitePref command) {
		try {
			Long userId = getUserId(request);
			Long plId = getPrivateLabelId(request);
			Set<String> supportedPreferences = SupportedPreferenceConfig.getSuportedPreferences(plId);
			command.setSupportedPreferences(supportedPreferences);
			getMyAccountService().saveDefaultCheckout(userId, plId, null, command);
		}
		catch (SessionTimeoutException ex) {
		}
	}
	
	public void submitSitePrefDefaultOrg(HttpServletRequest request, MyAccountCommandSitePref command) {
		try {
			Long userId = getUserId(request);
			UserLoginInfo userLoginInfo = sessionSupport.getAuthenticatedUser(request);
			if(userLoginInfo.isMultipleOrganizations()) {
				String defaultOrg = command.getDefaultOrg();
				if(Util.isNotEmpty(defaultOrg)) {
					getMyAccountService().saveOrUpdateDefaultOrg(userId, defaultOrg, getPrivateLabelId(request));			
					request.getSession().setAttribute(SessionSupport.CURRENT_ORG_TO_DEFAULT_ORG, "true");
				}
			}
		}
		catch (Exception execption) {
			execption.printStackTrace();
		}
	}
	
	public void submitSitePrefQuickBid(HttpServletRequest request, MyAccountCommandSitePref command) {
		
		try {
			Long userId = getUserId(request);
			getMyAccountService().saveQuickBidEnabled(userId, command);
		}
		catch (SessionTimeoutException ex) {
		}
	}
	
	public void submitSitePrefNewWave(HttpServletRequest request, MyAccountCommandSitePref command) {
		
		try {
			Long userId = getUserId(request);
			getMyAccountService().saveNewWaveEnable(userId, command);
			if(cacheService != null){
				cacheService.putNewWaveRedirectLoginIdToCache(userId, null);//clear cache for NewWave Redirect
			}
		}
		catch (SessionTimeoutException ex) {
		}
	}
	
	public void submitSitePrefRunlist(HttpServletRequest request, MyAccountCommandSitePref command) {
		
		try {
			Long userId = getUserId(request);
			String value="0";
			if (command!=null && command.getUserRunlistPreference()!=null) {
				if (command.getUserRunlistPreference().equals(true)) {
					value="1";
				}
			}
			getUserService().saveUserRunlistPreference(userId,value);
		}
		catch (SessionTimeoutException ex) {
		}
	}
	
	public void saveUserTimeZone(HttpServletRequest request, MyAccountCommandSitePref command){
		try{
			Long loginId = getUserId(request);
			
			String tzFromForm = Util.toString(command.getTimezone());
			if(tzFromForm.equals(LOCAL_TIME_ZONE)){
				List<KeyValuePair> list = new ArrayList<KeyValuePair>();
				String userMachineTimeZone = getSessionSupport().getUserMachineTimeZone(request);
				list.add(new KeyValuePair(getTimeZonePreferenceName(request), userMachineTimeZone));
				list.add(new KeyValuePair(getTimeZoneUseLocalPreferenceName(request), "1"));
				getMyAccountService().addOrInsertLoginPreferences(loginId, list);
				return;
			}
			
			List<KeyValuePair> list = new ArrayList<KeyValuePair>();
			list.add(new KeyValuePair(getTimeZonePreferenceName(request), tzFromForm));
			list.add(new KeyValuePair(getTimeZoneUseLocalPreferenceName(request), "0"));
			getMyAccountService().addOrInsertLoginPreferences(loginId, list);
		}
		catch(Exception e){
			logger.error(e.getMessage(), e);
		}
	}
	
	public void submitSitePrefUserLanguage(HttpServletRequest request, MyAccountCommandSitePref command) {		
		try {
			Long userId = getUserId(request);
			Integer userLangPrefVal = command.getUserLangPref();
			if(Util.isNotEmpty(userLangPrefVal)){
				List<KeyValuePair> list = new ArrayList<KeyValuePair>();
				list.add(new KeyValuePair(USER_PREFERENCE_NAME.USER_LANGUAGE.toString(), userLangPrefVal.toString()));
				getMyAccountService().addOrInsertLoginPreferences(userId, list);
				int userLanguageId = userLangPrefVal.intValue();
				if(userLanguageId == 2){
					WebUtils.setSessionAttribute(request, SessionLocaleResolver.LOCALE_SESSION_ATTRIBUTE_NAME,Locale.CANADA_FRENCH);
					logger.info("In My Adesa Site Preference Language Version that got set to the session is "+Locale.CANADA_FRENCH);
				}else if(userLanguageId == 1){
					WebUtils.setSessionAttribute(request, SessionLocaleResolver.LOCALE_SESSION_ATTRIBUTE_NAME,Locale.US);
					logger.info("In My Adesa Site Preference Language Version that got set to the session is "+Locale.US);
				}
				request.setAttribute("ulangPref", userLanguageId);
			}
		}
		catch(SessionTimeoutException stex){stex.printStackTrace();}
		catch(Exception ex){ex.printStackTrace();}
	}

	public void submitSearchVersionPreference ( HttpServletRequest request, HttpServletResponse response, MyAccountCommandSitePref command ) {
		Boolean isNewSearch = command.getIsNewSearchEnabled();
		searchVersionSupport.getConfiguration().setNewSearch(request, response, isNewSearch);
		if (isNewSearch) request.setAttribute(RequestSupport.AUCTION_SEARCH_ENGINE_SUPPORT_NAME, true);
		else request.removeAttribute(RequestSupport.AUCTION_SEARCH_ENGINE_SUPPORT_NAME);
	}

	public SessionSupport getSessionSupport() {
		return sessionSupport;
	}

	@SuppressWarnings("all")
	@Override
	protected Map referenceData(HttpServletRequest request) throws Exception {
		ModelMap modelMap = (ModelMap) super.referenceData( request );
		// check out options
		DefaultCheckoutInfo defaultCheckoutInfo = getDefaultCheckoutInfo(request);
		modelMap.addAttribute("defaultCheckoutPaymentMethods", defaultCheckoutInfo.getPmList());
		modelMap.addAttribute("defaultCheckoutTransportOptions", defaultCheckoutInfo.getTransportList());
		
		List<Service> titleShippingServices = defaultCheckoutInfo.getTitleShippingServices();
		if(!Util.isEmpty(titleShippingServices)){
			modelMap.addAttribute("titleShippingServices", titleShippingServices);
			modelMap.addAttribute("hasTitleShippingServices", true);
		}
		
		String searchConfigValue = searchVersionSupport.getConfiguration().getNewSearchSystemConfiguration();
		
		if ( searchConfigValue != null ) searchConfigValue = searchConfigValue.toLowerCase();
		
		Long plId = getPrivateLabelId(request);
		modelMap.addAttribute("supportedPreferences", SupportedPreferenceConfig.getSuportedPreferencesAsMap(plId));
		modelMap.addAttribute("searchConfigValue", searchConfigValue );
		
		String[] timezones = getTimezones();
		if(timezones != null && timezones.length > 0){
			modelMap.addAttribute("timezones", timezones);
		}
		
		modelMap.addAttribute("LOCAL_TIME_ZONE", LOCAL_TIME_ZONE);
		
		// all user auction channel
		if (Global.isAuctionChannelPrefEnabledByPrivateLableId(plId)) {
			Locale locale = RequestSupport.getRequestPrivateLabelLocale(request);
			List<KeyValuePairGen<String, String>> allAuctionChannelCheckList = getAllAuctionChannelCheckList(locale);
			modelMap.addAttribute("allAuctionChannelList", allAuctionChannelCheckList);
		}
		//
		
		// get default org
		Long userId = getUserId(request);
		String defaultOrg=null;
		if (userId!=null && plId!=null) {
			defaultOrg= getUserService().getUserDefaultOrg(userId, plId);
		}
		if (Util.isNotEmpty(defaultOrg)){
			modelMap.addAttribute("defaultUserPreferenceOrgId",defaultOrg);
		}

		
		//favorite auctions search url
		String myTransactionPurchaseApiBaseUrl = Global.getNonEmptyApplicationConfigurations().get(MY_FAV_AUC_SEARCH_PREFIX);
		if (!Util.isEmptyString(myTransactionPurchaseApiBaseUrl)) {
			myTransactionPurchaseApiBaseUrl = myTransactionPurchaseApiBaseUrl.trim();
			if (!myTransactionPurchaseApiBaseUrl.endsWith("/")) {
				myTransactionPurchaseApiBaseUrl += "/";
			}
			
			myTransactionPurchaseApiBaseUrl += TRANSACTIONS_SEARCHER_REAL10;
			modelMap.addAttribute("myFavoriteAuctionsSearchBaseUrl", myTransactionPurchaseApiBaseUrl);
		}
		
		return modelMap;
	}

	protected String[] getTimezones(){
    	return Util.getAllTimeZones();
    }
	
	@Override
	protected Object formBackingObject(HttpServletRequest request)
			throws Exception {

		// checkout options
		MyAccountCommandSitePref myAccountCommand = new MyAccountCommandSitePref();
		Long userId = getUserId(request);
		Long plId = getPrivateLabelId(request);
		// check out options
		DefaultCheckoutInfo defaultCheckoutInfo = getDefaultCheckoutInfo(request);
		myAccountCommand.copyProperties(defaultCheckoutInfo.getCommand());

		// quick bid
		QuickBidCommand quickBidCommand;
		quickBidCommand = getMyAccountService().getQuickBidEnabled(userId);
		if (quickBidCommand != null) {
			myAccountCommand.setEnabled(quickBidCommand.getEnabled());
		}
		// new wave
		NewWaveCommand newWaveCommand;
		newWaveCommand = getMyAccountService().getNewWaveEnable(userId);
		if (newWaveCommand != null) {
			myAccountCommand.setEnable(newWaveCommand.getEnable());
		}
		
		
		// user runlist preference
		if (Global.RUNLIST_ENABLED) {
		    String userRunlistStr = getUserService().findUserRunlistPreferenceByLoginId(userId);
		    Boolean userRunlistFlag =  Util.toBoolean(userRunlistStr);
		    myAccountCommand.setUserRunlistPreference(userRunlistFlag);
		}
		// user auction channel preference
		if (Global.isAuctionChannelPrefEnabledByPrivateLableId(plId)){
			List<String> userAuctionChannel = getUserService().findUserAuctionChannelPreferenceByLoginId(userId);
			myAccountCommand.setUserAuctionChannelPreference(userAuctionChannel);
		}
		//
		Boolean isNewSearch = searchVersionSupport.getConfiguration().isNewSearch( request );
		String systemConfiguration = searchVersionSupport.getConfiguration().getNewSearchSystemConfiguration();
		myAccountCommand.setIsNewSearchEnabled(isNewSearch);
		myAccountCommand.setNewSearchSystemConfiguration(systemConfiguration);
		
		Long isBetaTesterPrefId = getUserService().getPreferenceIdByName(Preference.USER_NEWWAVE_WL_BETA_TESTER);
		boolean isBetaTester = true;
		String value = getUserService().getUserPreferenceValueById(userId, isBetaTesterPrefId);
		if (null != value) {
			isBetaTester = Util.toBoolean(value);
		}
		myAccountCommand.setIsBetaTester(isBetaTester);
		
		String initialTimeZone = getInitialTimeZone(request, userId);
		if(!Util.isEmpty(initialTimeZone)){
			myAccountCommand.setTimezone(initialTimeZone);
		}
		
		return myAccountCommand;
	}
	
	/**
	 * Time Zone to use when the page loads
	 * @param request
	 * @param loginId
	 * @return
	 */
	private String getInitialTimeZone(HttpServletRequest request, Long loginId){
		boolean useLocalTimeZone = findUserWantsToUseLocalTimeZone(loginId, request);
		if(useLocalTimeZone) return LOCAL_TIME_ZONE;

		String userTimeZone = findUserTimeZone(loginId, request);
		if(!Util.isEmpty(userTimeZone)){
			return userTimeZone;
		}

		return LOCAL_TIME_ZONE;
	}

	/**
	 * Checks the db for the user's preferred time zone.
	 * @param loginId
	 * @param request
	 * @return
	 */
	private String findUserTimeZone(Long loginId, HttpServletRequest request){
		try{
			String userTimeZone = getMyAccountService().getPreferenceValueByName(loginId, getTimeZonePreferenceName(request));
			return userTimeZone;
		}
		catch(Exception e){
			logger.error(ESAPI.encoder().encodeForHTML("ERROR when finding the time zone for loginId " + loginId + ": " + e.getMessage()), e);
			return null;
		}
	}
	
	/**
	 * Checks the db to see if the user wants to use the local time zone. 
	 * @param loginId
	 * @param request
	 * @return
	 */
	private boolean findUserWantsToUseLocalTimeZone(Long loginId, HttpServletRequest request){
		try{
			String value = getMyAccountService().getPreferenceValueByName(loginId, getTimeZoneUseLocalPreferenceName(request));
			boolean useLocalTimeZone = Util.toBoolean(value);
			return useLocalTimeZone;
		}
		catch(Exception e){
			logger.error("ERROR when determining if user wants to use local time zone", e);
			return false;
		}
	}
	
	@Override
	protected boolean isFormSubmission(HttpServletRequest request) {
		boolean saveSubmit = Util.toBoolean(request.getParameter("save"))
				|| Util.toBoolean(request.getParameter("save.x"))
				||
				(request.getParameter("myAccountChangeTabSave") != null && request.getParameter("myAccountChangeTabSave").equals("true")
				);
		return saveSubmit;
	}

	@Override
	protected ModelAndView onSubmit(HttpServletRequest request,
			HttpServletResponse response, Object command, BindException errors)
			throws Exception {
		// 
		MyAccountCommandSitePref myAccountcommand = (MyAccountCommandSitePref) command;
		String myAccountTabUrl = getMyAccountTabUrlFromRequest(request);
		submitSitePrefCheckout(request, myAccountcommand);
		submitSitePrefQuickBid(request, myAccountcommand);
		submitSitePrefDefaultOrg(request, myAccountcommand);
		// user auction channel preference
		Long plId = getPrivateLabelId(request);
		if (Global.isAuctionChannelPrefEnabledByPrivateLableId(plId)){
			Long userId = getUserId(request);
			List<String> userAuctionChannel = myAccountcommand.getUserAuctionChannelPreference();
			if (Util.isNotEmpty(userAuctionChannel)) {
				getUserService().saveUserAuctionChannelPreference(userId, userAuctionChannel);
				String auctionChannelsStr = Util.getStringFromListSeparateByComma(userAuctionChannel, "", "");
				request.getSession().setAttribute(SessionSupport.USER_AUCTION_CHANNEL_PREF_STR,auctionChannelsStr);
			}
			else {
				getUserService().removeUserAuctionChannelPreference(userId);
				String auctionChannelsStr = "";
				request.getSession().setAttribute(SessionSupport.USER_AUCTION_CHANNEL_PREF_STR,auctionChannelsStr);
			}
		}
		//
		saveUserTimeZone(request, myAccountcommand);
		sessionSupport.setUserTimeZone(request, null); // remove it from the session so that it is read from the db on the next page
		submitSitePrefUserLanguage(request, myAccountcommand);
		if (  StringUtils.isNotEmpty( myAccountcommand.getNewSearchSystemConfiguration() ) ) {
			submitSearchVersionPreference(request, response, myAccountcommand );
		}
		
		String hiddenDefaultOrg = request.getParameter("hiddenDefaultOrg");
		if(hiddenDefaultOrg != null && !hiddenDefaultOrg.equals("")) {
			// try to validate before set to session. ignore if there is any error
			try {
				hiddenDefaultOrg = ESAPI.validator().getValidInput("hiddenDefaultOrg", hiddenDefaultOrg, "HTTPParameterValue", HTTPUtilities2.MAX_STR_LENGHT, false, false);
				request.getSession().setAttribute(sessionSupport.DEFAULT_ORGANIZATION_ID, hiddenDefaultOrg);
			} catch (ValidationException e) {
			} catch (IntrusionException e) {
			}
		}
	
		ModelAndView mv= showForm(request, response, errors);
		
		
		if (!errors.hasErrors()) {			
			if (Util.isNotEmpty(myAccountTabUrl)) {
				// redirect to other my account tab url
				//return new ModelAndView("redirect:/" + myAccountTabUrl + ".html");
				mv.addObject("successfulUpdateRedirect", true);
				mv.addObject("successfulUpdateRedirectTabUrl",myAccountTabUrl + ".html");
			}else {
				mv.addObject("savedSuccessFlag",Boolean.TRUE);
			}
		}
		return mv;
	}
	
	protected String getTimeZonePreferenceName(HttpServletRequest request){
		return  USER_PREFERENCE_NAME.USER_TIME_ZONE.toString();
	}
	
	protected String getTimeZoneUseLocalPreferenceName(HttpServletRequest request){
		return  USER_PREFERENCE_NAME.USER_TIME_ZONE_USE_LOCAL.toString();
	}
	
	@Override
	protected boolean includeErrorsAsParametersInRequest(){
		return true;
	}
	
}
