package misk.hibernate

import com.google.crypto.tink.Aead
import misk.crypto.AeadKeyManager
import misk.logging.getLogger
import org.hibernate.HibernateException
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.usertype.ParameterizedType
import org.hibernate.usertype.UserType
import java.io.Serializable
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.util.Properties
import org.hibernate.type.spi.TypeConfiguration
import org.hibernate.type.spi.TypeConfigurationAware
import java.security.GeneralSecurityException
import java.util.Base64

internal class SecretColumnType : UserType, ParameterizedType, TypeConfigurationAware {
  companion object {
    const val FIELD_ENCRYPTION_KEY_NAME: String = "key_name"
    val logger = getLogger<SecretColumnType>()
  }
  private lateinit var aead: Aead
  private lateinit var keyName: String
  private lateinit var _typeConfiguration: TypeConfiguration

  override fun setTypeConfiguration(typeConfiguration: TypeConfiguration) {
    _typeConfiguration = typeConfiguration
  }

  override fun getTypeConfiguration(): TypeConfiguration = _typeConfiguration

  override fun setParameterValues(parameters: Properties) {
    val keyManager = _typeConfiguration.metadataBuildingContext.bootstrapContext.serviceRegistry.injector
        .getInstance(AeadKeyManager::class.java)
    keyName = parameters.getProperty(FIELD_ENCRYPTION_KEY_NAME)
    aead = keyManager[keyName] ?:
        throw HibernateException("Cannot set field, key $keyName not found")
  }

  override fun hashCode(x: Any): Int = aead.decrypt(x as ByteArray, null).hashCode()

  override fun deepCopy(value: Any?) = (value as ByteArray?)?.copyOf()

  override fun replace(original: Any?, target: Any?, owner: Any?) = (original as ByteArray).copyOf()

  override fun equals(x: Any?, y: Any?): Boolean {
    if (x == null && y == null) {
      return true
    }
    if (x == null && y !== null) {
      return false
    }
    if (x != null && y == null) {
      return false
    }
    return try {
      aead.decrypt(x as ByteArray, null) contentEquals
          (aead.decrypt(y as ByteArray, null))
    } catch (e: GeneralSecurityException) {
      false
    }
  }

  override fun returnedClass() = ByteArray::class.java

  override fun assemble(cached: Serializable?, owner: Any?) = cached

  override fun disassemble(value: Any?) = (value as ByteArray).copyOf() as Serializable

  override fun nullSafeSet(
    st: PreparedStatement,
    value: Any?,
    index: Int,
    session: SharedSessionContractImplementor?
  ) {
    if (value == null) {
      st.setNull(index, Types.VARBINARY)
    } else {
      val encrypted = aead.encrypt(value as ByteArray, null)
      st.setBytes(index, encrypted)
    }
  }

  override fun nullSafeGet(
    rs: ResultSet?,
    names: Array<out String>,
    session: SharedSessionContractImplementor?,
    owner: Any?
  ): Any? {
    val result = rs?.getBytes(names[0])
    return result?.let { try {
      aead.decrypt(it, null)
    } catch (e: GeneralSecurityException) {
      logger.warn { "Failed to decrypt ${Base64.getEncoder().encodeToString(it)} using key $keyName" }
      null
    }}
  }

  override fun isMutable() = false

  override fun sqlTypes() = intArrayOf(Types.VARBINARY)
}