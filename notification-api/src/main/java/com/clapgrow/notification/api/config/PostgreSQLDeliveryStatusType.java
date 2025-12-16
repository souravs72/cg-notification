package com.clapgrow.notification.api.config;

import com.clapgrow.notification.api.enums.DeliveryStatus;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;
import org.postgresql.util.PGobject;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

public class PostgreSQLDeliveryStatusType implements UserType<DeliveryStatus> {
    
    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<DeliveryStatus> returnedClass() {
        return DeliveryStatus.class;
    }

    @Override
    public boolean equals(DeliveryStatus x, DeliveryStatus y) throws HibernateException {
        return x == y;
    }

    @Override
    public int hashCode(DeliveryStatus x) throws HibernateException {
        return x == null ? 0 : x.hashCode();
    }

    @Override
    public DeliveryStatus nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) 
            throws SQLException {
        Object pgObject = rs.getObject(position);
        if (pgObject == null) {
            return null;
        }
        String value;
        if (pgObject instanceof PGobject) {
            value = ((PGobject) pgObject).getValue();
        } else {
            value = pgObject.toString();
        }
        return value != null ? DeliveryStatus.valueOf(value) : null;
    }

    @Override
    public void nullSafeSet(PreparedStatement st, DeliveryStatus value, int index, SharedSessionContractImplementor session) 
            throws HibernateException, SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            PGobject pgObject = new PGobject();
            pgObject.setType("delivery_status");
            pgObject.setValue(value.name());
            st.setObject(index, pgObject, Types.OTHER);
        }
    }

    @Override
    public DeliveryStatus deepCopy(DeliveryStatus value) throws HibernateException {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(DeliveryStatus value) throws HibernateException {
        return value;
    }

    @Override
    public DeliveryStatus assemble(Serializable cached, Object owner) throws HibernateException {
        return (DeliveryStatus) cached;
    }

    @Override
    public DeliveryStatus replace(DeliveryStatus original, DeliveryStatus target, Object owner) throws HibernateException {
        return original;
    }
}










