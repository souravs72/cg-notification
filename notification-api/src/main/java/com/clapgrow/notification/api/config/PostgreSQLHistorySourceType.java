package com.clapgrow.notification.api.config;

import com.clapgrow.notification.api.enums.HistorySource;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;
import org.postgresql.util.PGobject;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

public class PostgreSQLHistorySourceType implements UserType<HistorySource> {
    
    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<HistorySource> returnedClass() {
        return HistorySource.class;
    }

    @Override
    public boolean equals(HistorySource x, HistorySource y) throws HibernateException {
        return x == y;
    }

    @Override
    public int hashCode(HistorySource x) throws HibernateException {
        return x == null ? 0 : x.hashCode();
    }

    @Override
    public HistorySource nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) 
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
        return value != null ? HistorySource.valueOf(value) : null;
    }

    @Override
    public void nullSafeSet(PreparedStatement st, HistorySource value, int index, SharedSessionContractImplementor session) 
            throws HibernateException, SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            PGobject pgObject = new PGobject();
            pgObject.setType("history_source");
            pgObject.setValue(value.name());
            st.setObject(index, pgObject, Types.OTHER);
        }
    }

    @Override
    public HistorySource deepCopy(HistorySource value) throws HibernateException {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(HistorySource value) throws HibernateException {
        return value;
    }

    @Override
    public HistorySource assemble(Serializable cached, Object owner) throws HibernateException {
        return (HistorySource) cached;
    }

    @Override
    public HistorySource replace(HistorySource original, HistorySource target, Object owner) throws HibernateException {
        return original;
    }
}








