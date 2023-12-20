package ee.ria.DigiDoc.common;

import androidx.annotation.Nullable;

import java.net.Proxy;

import okhttp3.Authenticator;

public final class ProxyConfig {
    @Nullable
    private final Proxy proxy;
    private final Authenticator authenticator;

    public ProxyConfig(@Nullable Proxy proxy, Authenticator authenticator) {
        this.proxy = proxy;
        this.authenticator = authenticator;
    }

    @Nullable
    public Proxy proxy() {
        return proxy;
    }

    public Authenticator authenticator() {
        return authenticator;
    }
}
