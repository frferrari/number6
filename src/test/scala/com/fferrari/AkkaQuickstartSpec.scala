//#full-example
/*
package com.fferrari

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.fferrari.PriceScrapper.Greet
import com.fferrari.PriceScrapper.Greeted
import org.scalatest.wordspec.AnyWordSpecLike

//#definition
class AkkaQuickstartSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
//#definition

  "A Greeter" must {
    //#test
    "reply to greeted" in {
      val replyProbe = createTestProbe[Greeted]()
      val underTest = spawn(PriceScrapper())
      underTest ! Greet("Santa", replyProbe.ref)
      replyProbe.expectMessage(Greeted("Santa", underTest.ref))
    }
    //#test
  }

}
*/
//#full-example
