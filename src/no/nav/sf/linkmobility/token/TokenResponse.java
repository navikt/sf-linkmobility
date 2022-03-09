package no.nav.sf.linkmobility.token;

public class TokenResponse {
    private String access_token;
    private String scope;

    @Override
    public String toString() {
        return "no.nav.sf.linkmobility.token.TokenResponse{" +
                "access_token='" + access_token + '\'' +
                ", scope='" + scope + '\'' +
                ", instance_url='" + instance_url + '\'' +
                ", id='" + id + '\'' +
                ", token_type='" + token_type + '\'' +
                '}';
    }

    private String instance_url;
    private String id;
    private String token_type;

    public String getAccess_token() {
        return access_token;
    }

    public void setAccess_token(String access_token) {
        this.access_token = access_token;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getInstance_url() {
        return instance_url;
    }

    public void setInstance_url(String instance_url) {
        this.instance_url = instance_url;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getToken_type() {
        return token_type;
    }

    public void setToken_type(String token_type) {
        this.token_type = token_type;
    }
}
