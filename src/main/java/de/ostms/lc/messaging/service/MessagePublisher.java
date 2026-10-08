package de.ostms.lc.messaging.service;public interface MessagePublisher{void publish(String topic,String key,String payload);String provider();}
