package org.opendaylight.yang.svc.v1.urn.sma.sdn.csa.rev260831;

import java.lang.Override;
import java.util.ServiceLoader;
import org.opendaylight.yangtools.binding.meta.YangModelBindingProvider;
import org.opendaylight.yangtools.binding.meta.YangModuleInfo;

/**
 * The {@link YangModelBindingProvider} for {@code csa} module. This class should not be used
 * directly, but rather through {@link ServiceLoader}.
 */
@javax.annotation.processing.Generated("mdsal-binding-generator")
public final class YangModelBindingProviderImpl implements YangModelBindingProvider {
    /**
     * Construct a new provider.
     */
    public YangModelBindingProviderImpl() {
        // No-op
    }

    @Override
    public YangModuleInfo getModuleInfo() {
        return YangModuleInfoImpl.getInstance();
    }
}
