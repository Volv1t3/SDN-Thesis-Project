package org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state;
import com.google.common.base.MoreObjects;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.util.Objects;
import javax.annotation.processing.Generated;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.binding.Key;
import org.opendaylight.yangtools.binding.lib.CodeHelpers;

/**
 * This class represents the key of {@link BgpLsNode} class.
 *
 * @see BgpLsNode
 *
 */
@Generated("mdsal-binding-generator")
public final class BgpLsNodeKey
 implements Key<BgpLsNode> {
    @java.io.Serial
    private static final long serialVersionUID = 1086000696776134666L;
    private final String _routerId;


    /**
     * Constructs an instance.
     *
     * @param _routerId the entity routerId
     * @throws NullPointerException if any of the arguments are null
     */
    public BgpLsNodeKey(@NonNull String _routerId) {
        this._routerId = CodeHelpers.requireKeyProp(_routerId, "routerId");
    }
    
    /**
     * Creates a copy from Source Object.
     *
     * @param source Source object
     */
    public BgpLsNodeKey(BgpLsNodeKey source) {
        this._routerId = source._routerId;
    }


    /**
     * Return routerId, guaranteed to be non-null.
     *
     * @return {@code String} routerId, guaranteed to be non-null.
     */
    public @NonNull String getRouterId() {
        return _routerId;
    }


    @Override
    public int hashCode() {
        return CodeHelpers.wrapperHashCode(_routerId);
    }

    @Override
    public final boolean equals(Object obj) {
        return this == obj || obj instanceof BgpLsNodeKey other
            && Objects.equals(_routerId, other._routerId);
    }

    @Override
    public String toString() {
        final var helper = MoreObjects.toStringHelper(BgpLsNodeKey.class);
        CodeHelpers.appendValue(helper, "routerId", _routerId);
        return helper.toString();
    }
}

