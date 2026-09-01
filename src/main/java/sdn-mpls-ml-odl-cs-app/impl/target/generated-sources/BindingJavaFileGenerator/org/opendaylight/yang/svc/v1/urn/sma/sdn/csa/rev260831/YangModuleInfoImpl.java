package org.opendaylight.yang.svc.v1.urn.sma.sdn.csa.rev260831;

import com.google.common.collect.ImmutableSet;
import java.lang.Override;
import java.lang.String;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.binding.lib.ResourceYangModuleInfo;
import org.opendaylight.yangtools.binding.meta.YangModuleInfo;
import org.opendaylight.yangtools.yang.common.QName;

/**
 * The {@link ResourceYangModuleInfo} for {@code csa} module.
 */
@javax.annotation.processing.Generated("mdsal-binding-generator")
public final class YangModuleInfoImpl extends ResourceYangModuleInfo {
    private static final @NonNull QName NAME = QName.create("urn:sma:sdn:csa", "2026-08-31", "csa").intern();
    private static final @NonNull YangModuleInfo INSTANCE = new YangModuleInfoImpl();

    private final @NonNull ImmutableSet<YangModuleInfo> importedModules;

    /**
     * Return the singleton instance of this class.
     *
     * @return The singleton instance
     */
    public static @NonNull YangModuleInfo getInstance() {
        return INSTANCE;
    }

    /**
     * Create an interned {@link QName} with specified {@code localName} and namespace/revision of this
     * module.
     *
     * @param localName local name
     * @return A QName
     * @throws NullPointerException if {@code localName} is {@code null}
     * @throws IllegalArgumentException if {@code localName} is not a valid YANG identifier
     */
    public static @NonNull QName qnameOf(final String localName) {
        return QName.create(NAME, localName).intern();
    }

    private YangModuleInfoImpl() {
        importedModules = ImmutableSet.of();
    }
    
    @Override
    public QName getName() {
        return NAME;
    }
    
    @Override
    protected String resourceName() {
        return "/META-INF/yang/csa@2026-08-31.yang";
    }
    
    @Override
    public ImmutableSet<YangModuleInfo> getImportedModules() {
        return importedModules;
    }
}
