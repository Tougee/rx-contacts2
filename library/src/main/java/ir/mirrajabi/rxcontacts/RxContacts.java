/*
 * Copyright (C) 2016 Ulrich Raab.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ir.mirrajabi.rxcontacts;


import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.util.LongSparseArray;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;

import static ir.mirrajabi.rxcontacts.ColumnMapper.mapDisplayName;
import static ir.mirrajabi.rxcontacts.ColumnMapper.mapEmail;
import static ir.mirrajabi.rxcontacts.ColumnMapper.mapInVisibleGroup;
import static ir.mirrajabi.rxcontacts.ColumnMapper.mapPhoneNumber;
import static ir.mirrajabi.rxcontacts.ColumnMapper.mapPhoto;
import static ir.mirrajabi.rxcontacts.ColumnMapper.mapStarred;
import static ir.mirrajabi.rxcontacts.ColumnMapper.mapThumbnail;


/**
 * Android contacts as rx observable.
 *
 * @author Ulrich Raab
 * @author MADNESS
 */
public class RxContacts {
    private static final String[] PROJECTION = {
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.DISPLAY_NAME_PRIMARY,
            ContactsContract.Data.STARRED,
            ContactsContract.Data.PHOTO_URI,
            ContactsContract.Data.PHOTO_THUMBNAIL_URI,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.IN_VISIBLE_GROUP
    };

    private ContentResolver mResolver;

    public static Observable<Contact> fetch(@NonNull final Context context) {
        return Observable.create(new ObservableOnSubscribe<Contact>() {
            @Override
            public void subscribe(@io.reactivex.annotations.NonNull
                                          ObservableEmitter<Contact> e) throws Exception {
                new RxContacts(context).fetch(e);
            }
        });
    }

    private RxContacts(@NonNull Context context) {
        mResolver = context.getContentResolver();
    }


    private void fetch(ObservableEmitter emitter) {
        LongSparseArray<Contact> contacts = new LongSparseArray<>();
        Cursor cursor = createCursor();
        if (cursor == null) {
            emitter.onError(new IllegalArgumentException());
            return;
        }
        cursor.moveToFirst();
        // Get the column indexes
        int idxId = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID);
        int idxInVisibleGroup = cursor.getColumnIndex(ContactsContract.Data.IN_VISIBLE_GROUP);
        int idxDisplayNamePrimary = cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME_PRIMARY);
        int idxStarred = cursor.getColumnIndex(ContactsContract.Data.STARRED);
        int idxPhoto = cursor.getColumnIndex(ContactsContract.Data.PHOTO_URI);
        int idxThumbnail = cursor.getColumnIndex(ContactsContract.Data.PHOTO_THUMBNAIL_URI);
        int idxMimetype = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE);
        int idxData1 = cursor.getColumnIndex(ContactsContract.Data.DATA1);
        // Map the columns to the fields of the contact
        while (!cursor.isAfterLast()) {
            // Get the id and the contact for this id. The contact may be a null.
            long id = cursor.getLong(idxId);
            Contact contact = contacts.get(id, null);
            if (contact == null) {
                // Create a new contact
                contact = new Contact(id);
                // Map the non collection attributes
                mapInVisibleGroup(cursor, contact, idxInVisibleGroup);
                mapDisplayName(cursor, contact, idxDisplayNamePrimary);
                mapStarred(cursor, contact, idxStarred);
                mapPhoto(cursor, contact, idxPhoto);
                mapThumbnail(cursor, contact, idxThumbnail);
                // Add the contact to the collection
                contacts.put(id, contact);
            }

            // map phone number or email address
            String mimetype = cursor.getString(idxMimetype);
            switch (mimetype) {
                case ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE: {
                    mapEmail(cursor, contact, idxData1);
                    break;
                }
                case ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE: {
                    mapPhoneNumber(cursor, contact, idxData1);
                    break;
                }
            }

            cursor.moveToNext();
        }

        cursor.close();
        for (int i = 0; i < contacts.size(); i++)
            emitter.onNext(contacts.valueAt(i));
        emitter.onComplete();
    }

    private Cursor createCursor() {
        return mResolver.query(
                ContactsContract.Data.CONTENT_URI,
                PROJECTION,
                null,
                null,
                ContactsContract.Data.CONTACT_ID
        );
    }
}
