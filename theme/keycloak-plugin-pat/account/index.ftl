<!DOCTYPE html>
<html lang="${locale}">
<head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <meta name="robots" content="noindex, nofollow"/>
    <title>${msg("accountManagementTitle")}</title>
    <link rel="stylesheet" href="${resourceUrl}/keycloak-account-ui.css"/>
    <link rel="stylesheet" href="${resourceUrl}/style.css"/>
</head>
<body>
<noscript>${msg("noJavascript")}</noscript>
<div id="root"></div>
<script id="environment" type="application/json">
{
  "serverBaseUrl": "${serverBaseUrl?json_string}",
  "realm": "${realm.name?json_string}",
  "clientId": "${clientId?json_string}",
  "resourceUrl": "${resourceUrl?json_string}",
  "baseUrl": "${baseUrl.path?json_string}",
  "locale": "${locale?json_string}",
  "logo": "${(properties.logo!"")?json_string}",
  "logoUrl": "${(properties.logoUrl!"")?json_string}",
  "features": {
    "isRegistrationEmailAsUsername": ${realm.registrationEmailAsUsername?c},
    "isEditUserNameAllowed": ${realm.editUsernameAllowed?c},
    "isLinkedAccountsEnabled": ${isLinkedAccountsEnabled?c},
    "isMyResourcesEnabled": ${isAuthorizationEnabled?c},
    "deleteAccountAllowed": ${deleteAccountAllowed?c},
    "updateEmailFeatureEnabled": ${updateEmailFeatureEnabled?c},
    "updateEmailActionEnabled": ${updateEmailActionEnabled?c},
    "isViewGroupsEnabled": ${isViewGroupsEnabled?c},
    "isViewOrganizationsEnabled": ${isViewOrganizationsEnabled?c},
    "isOid4VciEnabled": ${isOid4VciEnabled?c}
  }<#if referrerName??>,
  "referrerName": "${referrerName?json_string}",
  "referrerUrl": "${referrer_uri?json_string}"</#if>
}
</script>
<script type="module" src="${resourceUrl}/content.js"></script>
</body>
</html>
