package com.eh.digiatalpathalogy.admin.model;

public record EntityChangeNotification<T>(String key, String entityType, T oldData, T newData) {
}