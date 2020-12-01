package com.fferrari.model

case class WebsiteConfig(website: Website,
                         url: String,
                         lastScrappedUrl: Option[String])
