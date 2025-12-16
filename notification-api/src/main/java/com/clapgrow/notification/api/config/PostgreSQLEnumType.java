package com.clapgrow.notification.api.config;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;
import org.postgresql.util.PGobject;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

public class PostgreSQLEnumType implements UserType<Object> {
    
    private String enumTypeName;
    
    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<Object> returnedClass() {
        return Object.class;
    }

    @Override
    public boolean equals(Object x, Object y) throws HibernateException {
        return x == y || (x != null && x.equals(y));
    }

    @Override
    public int hashCode(Object x) throws HibernateException {
        return x == null ? 0 : x.hashCode();
    }

    @Override
    public Object nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) 
            throws SQLException {
        Object pgObject = rs.getObject(position);
        if (pgObject == null) {
            return null;
        }
        if (pgObject instanceof PGobject) {
            return ((PGobject) pgObject).getValue();
        }
        return pgObject.toString();
    }

    @Override
    public void nullSafeSet(PreparedStatement st, Object value, int index, SharedSessionContractImplementor session) 
            throws HibernateException, SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            // Determine enum type name from the column definition
            // This will be set by Hibernate based on the columnDefinition
            String enumType = getEnumTypeName(session, index);
            PGobject pgObject = new PGobject();
            pgObject.setType(enumType);
            pgObject.setValue(value.toString());
            st.setObject(index, pgObject, Types.OTHER);
        }
    }
    
    private String getEnumTypeName(SharedSessionContractImplementor session, int index) {
        // Try to get enum type from metadata - this is a simplified approach
        // For channel, it's "notification_channel", for status it's "delivery_status"
        // We'll determine this based on the column name or use a default
        return enumTypeName != null ? enumTypeName : "notification_channel";
    }
    
    public void setEnumTypeName(String enumTypeName) {
        this.enumTypeName = enumTypeName;
    }

    @Override
    public Object deepCopy(Object value) throws HibernateException {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(Object value) throws HibernateException {
        return (Serializable) value;
    }

    @Override
    public Object assemble(Serializable cached, Object owner) throws HibernateException {
        return cached;
    }

    @Override
    public Object replace(Object original, Object target, Object owner) throws HibernateException {
        return original;
    }
}

