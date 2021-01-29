/*
package com.fferrari.pricescraper.dashboard.application

import java.sql.Connection
import java.sql.DriverManager

import akka.japi.function
import akka.projection.jdbc.JdbcSession

class PlainJdbcSession extends JdbcSession {

  lazy val conn = {
    Class.forName("org.postgresql.Driver")
    val c = DriverManager.getConnection("jdbc:postgresql:127.0.0.1:5432/number6;DB_CLOSE_DELAY=-1")
    c.setAutoCommit(false)
    c
  }

  override def withConnection[Result](func: function.Function[Connection, Result]): Result =
    func(conn)

  override def commit(): Unit = conn.commit()
  override def rollback(): Unit = conn.rollback()
  override def close(): Unit = conn.close()
}
*/