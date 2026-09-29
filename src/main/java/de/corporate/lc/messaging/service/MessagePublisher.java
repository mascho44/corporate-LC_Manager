package de.corporate.lc.messaging.service;public interface MessagePublisher{void publish(String topic,String key,String payload);String provider();}
