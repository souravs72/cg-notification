package com.clapgrow.notification.api.config;

import com.clapgrow.notification.api.enums.FailureType;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;
import org.postgresql.util.PGobject;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

public class PostgreSQLFailureTypeType implements UserType<FailureType> {
    
    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<FailureType> returnedClass() {
        return FailureType.class;
    }

    @Override
    public boolean equals(FailureType x, FailureType y) throws HibernateException {
        return x == y;
    }

    @Override
    public int hashCode(FailureType x) throws HibernateException {
        return x == null ? 0 : x.hashCode();
    }

    @Override
    public FailureType nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) 
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
        return value != null ? FailureType.valueOf(value) : null;
    }

    @Override
    public void nullSafeSet(PreparedStatement st, FailureType value, int index, SharedSessionContractImplementor session) 
            throws HibernateException, SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            PGobject pgObject = new PGobject();
            pgObject.setType("failure_type");
            pgObject.setValue(value.name());
            st.setObject(index, pgObject, Types.OTHER);
        }
    }

    @Override
    public FailureType deepCopy(FailureType value) throws HibernateException {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(FailureType value) throws HibernateException {
        return value;
    }

    @Override
    public FailureType assemble(Serializable cached, Object owner) throws HibernateException {
        return (FailureType) cached;
    }

    @Override
    public FailureType replace(FailureType original, FailureType target, Object owner) throws HibernateException {
        return original;
    }
}








