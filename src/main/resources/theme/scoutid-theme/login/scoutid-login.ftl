<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true; section>
    
    <#-- SECTION 1: The Header -->
    <#if section = "header">
        SCOUTID
    
    <#-- SECTION 2: The Form -->
    <#elseif section = "form">
        <form id="kc-form-login" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">
            
            <div class="${properties.kcFormGroupClass!}">
                <label for="username" class="${properties.kcLabelClass!}">Scoutnet Member No / Username</label>
                <input tabindex="1" id="username" class="${properties.kcInputClass!}" name="username" value="${(login.username!'')}"  type="text" autofocus autocomplete="off" />
            </div>

            <div class="${properties.kcFormGroupClass!}">
                <label for="password" class="${properties.kcLabelClass!}">Password</label>
                <input tabindex="2" id="password" class="${properties.kcInputClass!}" name="password" type="password" autocomplete="off" />
            </div>

            <div class="${properties.kcFormGroupClass!} ${properties.kcFormSettingClass!}">
                
                <div id="kc-form-options">
                    <div class="checkbox">
                        <label>
                            <input tabindex="3" id="rememberMe" name="rememberMe" type="checkbox"> Remember me
                        </label>
                    </div>
                </div>
                
                <div class="${properties.kcFormOptionsWrapperClass!}">
                    <span><a tabindex="5" href="https://www.scoutnet.se/reset-password-url" target="_blank">Forgot Password?</a></span>
                </div>
            </div>

            <div id="kc-form-buttons" class="${properties.kcFormGroupClass!}">
                <input type="hidden" id="id-hidden-input" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>/>
                <input tabindex="4" class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}" name="login" id="kc-login" type="submit" value="Sign In"/>
            </div>
        </form>
    
    <#-- SECTION 3: Info Text (Side panel or bottom text) -->
    <#elseif section = "info">
        <div id="scoutnet-info-area">
            <h4>Which username to use?</h4>
            <p>Please use your <strong>Scoutnet Member Number</strong> or your verified email address to log in.</p>
            <p>If this is your first time logging in, ensure your account is active in Scoutnet.</p>
        </div>
    </#if>
</@layout.registrationLayout>