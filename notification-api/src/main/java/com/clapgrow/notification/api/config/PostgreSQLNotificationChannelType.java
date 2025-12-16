package com.clapgrow.notification.api.config;

import com.clapgrow.notification.api.enums.NotificationChannel;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;
import org.postgresql.util.PGobject;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

public class PostgreSQLNotificationChannelType implements UserType<NotificationChannel> {
    
    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<NotificationChannel> returnedClass() {
        return NotificationChannel.class;
    }

    @Override
    public boolean equals(NotificationChannel x, NotificationChannel y) throws HibernateException {
        return x == y;
    }

    @Override
    public int hashCode(NotificationChannel x) throws HibernateException {
        return x == null ? 0 : x.hashCode();
    }

    @Override
    public NotificationChannel nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) 
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
        return value != null ? NotificationChannel.valueOf(value) : null;
    }

    @Override
    public void nullSafeSet(PreparedStatement st, NotificationChannel value, int index, SharedSessionContractImplementor session) 
            throws HibernateException, SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            PGobject pgObject = new PGobject();
            pgObject.setType("notification_channel");
            pgObject.setValue(value.name());
            st.setObject(index, pgObject, Types.OTHER);
        }
    }

    @Override
    public NotificationChannel deepCopy(NotificationChannel value) throws HibernateException {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(NotificationChannel value) throws HibernateException {
        return value;
    }

    @Override
    public NotificationChannel assemble(Serializable cached, Object owner) throws HibernateException {
        return (NotificationChannel) cached;
    }

    @Override
    public NotificationChannel replace(NotificationChannel original, NotificationChannel target, Object owner) throws HibernateException {
        return original;
    }
}










