package com.fferrari.pricescraper.service

import akka.actor.typed.ActorSystem
import com.typesafe.config.Config
import com.zaxxer.hikari.HikariDataSource
import scalikejdbc.ConnectionPool
import scalikejdbc.DataSourceCloser
import scalikejdbc.DataSourceConnectionPool
import scalikejdbc.config.DBs
import scalikejdbc.config.NoEnvPrefix
import scalikejdbc.config.TypesafeConfig
import scalikejdbc.config.TypesafeConfigReader

object ScalikeJdbcSetup {

  /**
   * Initiate the ScalikeJDBC connection pool configuration and shutdown.
   * The DataSource is setup with ActorSystem's config.
   *
   * The connection pool will be closed when the actor system terminates.
   */
  def init(system: ActorSystem[_]): Unit = {
    fromConfig(system.settings.config)
    system.whenTerminated.map { _ =>
      ConnectionPool.closeAll()
    }(scala.concurrent.ExecutionContext.Implicits.global)

  }

  /**
   * Builds a Hikari DataSource with values from jdbc-connection-settings.
   * The DataSource is then configured as the 'default' connection pool for ScalikeJDBC.
   */
  def fromConfig(config: Config): Unit = {

    val dbs = new DBsFromConfig(config)
    dbs.loadGlobalSettings()

    val dataSource = new HikariDataSource()

    dataSource.setPoolName("read-side-connection-pool")
    dataSource.setMaximumPoolSize(
      config.getInt("jdbc-connection-settings.connection-pool.max-pool-size"))

    val timeout =
      config
        .getDuration("jdbc-connection-settings.connection-pool.timeout")
        .toMillis
    dataSource.setConnectionTimeout(timeout)

    dataSource.setDriverClassName(config.getString("jdbc-connection-settings.driver"))
    println("=====> ScalikeJdbcSetup url = " + config.getString("jdbc-connection-settings.url"))
    println("=====> ScalikeJdbcSetup user = " + config.getString("jdbc-connection-settings.user"))
    println("=====> ScalikeJdbcSetup password = " + config.getString("jdbc-connection-settings.password"))
    dataSource.setJdbcUrl(config.getString("jdbc-connection-settings.url"))
    dataSource.setUsername(config.getString("jdbc-connection-settings.user"))
    dataSource.setPassword(config.getString("jdbc-connection-settings.password"))

    ConnectionPool.singleton(
      new DataSourceConnectionPool(
        dataSource = dataSource,
        closer = HikariCloser(dataSource)))
  }

  /**
   * This is only needed to allow ScalikeJdbc to load its logging configurations from the passed Config
   */
  private class DBsFromConfig(val config: Config)
    extends DBs
      with TypesafeConfigReader
      with TypesafeConfig
      with NoEnvPrefix

  /**
   * ScalikeJdbc needs a closer for the DataSource to delegate the closing call.
   */
  private case class HikariCloser(dataSource: HikariDataSource)
    extends DataSourceCloser {
    override def close(): Unit = dataSource.close()
  }

}