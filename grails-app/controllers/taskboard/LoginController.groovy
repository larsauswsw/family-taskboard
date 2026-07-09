package taskboard

class LoginController {

    /** Spring Security's default form-login failure handler redirects here
     *  with ?error on bad credentials (see SecurityConfig.groovy -- no
     *  explicit failureUrl is configured, so loginPage + "?error" is Spring
     *  Security's own default). loginFailed drives the error message in
     *  auth.gsp. */
    def auth() {
        [loginFailed: params.error != null]
    }

    def denied() {}
}
