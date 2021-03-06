/*
 * Copyright (C) 2016 The Android Open Source Project
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
package ru.kazakova_net.bookstore.data;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;

import static ru.kazakova_net.bookstore.data.BookContract.BookEntry;
import static ru.kazakova_net.bookstore.data.BookContract.CONTENT_AUTHORITY;
import static ru.kazakova_net.bookstore.data.BookContract.PATH_BOOKS;


/**
 * {@link ContentProvider} for BookStore app.
 */
public class BookStoreProvider extends ContentProvider {
    
    public static final String LOG_TAG = BookStoreProvider.class.getSimpleName();
    
    /**
     * URI matcher code for the content URI for the books table
     */
    private static final int BOOKS = 100;
    
    /**
     * URI matcher code for the content URI for a single book in the books table
     */
    private static final int BOOK_ID = 101;
    
    /**
     * UriMatcher object to match a content URI to a corresponding code.
     */
    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    
    static {
        // This URI is used to provide access to MULTIPLE rowsmof the books table.
        sUriMatcher.addURI(CONTENT_AUTHORITY, PATH_BOOKS, BOOKS);
        
        // This URI is used to provide access to ONE single row of the books table.
        sUriMatcher.addURI(CONTENT_AUTHORITY, PATH_BOOKS + "/#", BOOK_ID);
    }
    
    /**
     * Database helper object
     */
    private BookStoreDbHelper mBookStoreDbHelper;
    
    @Override
    public boolean onCreate() {
        mBookStoreDbHelper = new BookStoreDbHelper(getContext());
        return true;
    }
    
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        // Get readable database
        SQLiteDatabase database = mBookStoreDbHelper.getReadableDatabase();
        
        Cursor cursor;
        
        // Figure out if the URI matcher can match the URI to a specific code
        int match = sUriMatcher.match(uri);
        switch (match) {
            case BOOKS:
                // For the BOOKS code, query the books table directly with the given
                // projection, selection, selection arguments, and sort order. 
                cursor = database.query(BookEntry.TABLE_NAME, projection, selection, selectionArgs,
                        null, null, sortOrder);
                break;
            case BOOK_ID:
                // For the BOOK_ID code, extract out the ID from the URI.
                selection = BookEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};
                
                cursor = database.query(BookEntry.TABLE_NAME, projection, selection, selectionArgs,
                        null, null, sortOrder);
                break;
            default:
                throw new IllegalArgumentException("Cannot query unknown URI " + uri);
        }
        
        // Set notification URI on the Cursor.
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        
        // Return the cursor
        return cursor;
    }
    
    @Override
    public Uri insert(Uri uri, ContentValues contentValues) {
        final int match = sUriMatcher.match(uri);
        switch (match) {
            case BOOKS:
                return insertBook(uri, contentValues);
            default:
                throw new IllegalArgumentException("Insertion is not supported for " + uri);
        }
    }
    
    /**
     * Insert a book into the database with the given content values. Return the new content URI
     * for that specific row in the database.
     */
    private Uri insertBook(Uri uri, ContentValues values) {
        // Check that the title is not null
        String title = values.getAsString(BookEntry.COLUMN_BOOK_TITLE);
        if (title == null) {
            throw new IllegalArgumentException("Book requires a title");
        }
        
        // Check that the price is valid
        Integer price = values.getAsInteger(BookEntry.COLUMN_BOOK_PRICE);
        if (price == null || price < 0) {
            throw new IllegalArgumentException("Book requires valid price");
        }
        
        // Check that the quantity is valid
        Integer quantity = values.getAsInteger(BookEntry.COLUMN_BOOK_QUANTITY);
        if (quantity == null || quantity < 0) {
            throw new IllegalArgumentException("Book requires valid quantity");
        }
        
        // Check that the name of supplier is valid
        String supplierName = values.getAsString(BookEntry.COLUMN_BOOK_SUPPLIER_NAME);
        if (supplierName == null) {
            throw new IllegalArgumentException("Requires valid name of supplier");
        }
        
        // Check that the phone of supplier is valid
        String supplierPhone = values.getAsString(BookEntry.COLUMN_BOOK_SUPPLIER_PHONE);
        if (supplierPhone == null) {
            throw new IllegalArgumentException("Requires valid phone of supplier");
        }
        
        // Get writeable database
        SQLiteDatabase database = mBookStoreDbHelper.getWritableDatabase();
        
        // Insert the new book with the given values
        long id = database.insert(BookEntry.TABLE_NAME, null, values);
        // If the ID is -1, then the insertion failed. Log an error and return null.
        if (id == -1) {
            Log.e(LOG_TAG, "Failed to insert row for " + uri);
            
            return null;
        }
        
        // Notify all listeners that the data has changed for the book content URI
        getContext().getContentResolver().notifyChange(uri, null);
        
        // Return the new URI with the ID (of the newly inserted row) appended at the end
        return ContentUris.withAppendedId(uri, id);
    }
    
    @Override
    public int update(Uri uri, ContentValues contentValues, String selection,
                      String[] selectionArgs) {
        final int match = sUriMatcher.match(uri);
        
        switch (match) {
            case BOOKS:
                return updateBook(uri, contentValues, selection, selectionArgs);
            case BOOK_ID:
                selection = BookEntry._ID + "=?";
                
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};
                
                return updateBook(uri, contentValues, selection, selectionArgs);
            default:
                throw new IllegalArgumentException("Update is not supported for " + uri);
        }
    }
    
    /**
     * Update books in the database with the given content values. Apply the changes to the rows
     * specified in the selection and selection arguments (which could be 0 or 1 or more books).
     * Return the number of rows that were successfully updated.
     */
    private int updateBook(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // If the {@link BookEntry#COLUMN_BOOK_TITLE} key is present,
        // check that the title value is not null.
        if (values.containsKey(BookEntry.COLUMN_BOOK_TITLE)) {
            String title = values.getAsString(BookEntry.COLUMN_BOOK_TITLE);
            if (title == null) {
                throw new IllegalArgumentException("Book requires a title");
            }
        }
        
        // If the {@link BookEntry#COLUMN_BOOK_PRICE} key is present,
        // check that the price value is valid.
        if (values.containsKey(BookEntry.COLUMN_BOOK_PRICE)) {
            Integer price = values.getAsInteger(BookEntry.COLUMN_BOOK_PRICE);
            if (price == null || price < 0) {
                throw new IllegalArgumentException("Book requires valid price");
            }
        }
        
        // If the {@link BookEntry#COLUMN_BOOK_QUANTITY} key is present,
        // check that the quantity value is valid.
        if (values.containsKey(BookEntry.COLUMN_BOOK_QUANTITY)) {
            Integer quantity = values.getAsInteger(BookEntry.COLUMN_BOOK_QUANTITY);
            if (quantity == null || quantity < 0) {
                throw new IllegalArgumentException("Book requires valid quantity");
            }
        }
        
        // If the {@link BookEntry#COLUMN_BOOK_SUPPLIER_NAME} key is present,
        // check that the supplier name is not null.
        if (values.containsKey(BookEntry.COLUMN_BOOK_SUPPLIER_NAME)) {
            String supplierName = values.getAsString(BookEntry.COLUMN_BOOK_SUPPLIER_NAME);
            if (supplierName == null) {
                throw new IllegalArgumentException("Requires valid name of supplier");
            }
        }
        
        // If the {@link BookEntry#COLUMN_BOOK_SUPPLIER_PHONE} key is present,
        // check that the supplier phone is not null.
        if (values.containsKey(BookEntry.COLUMN_BOOK_SUPPLIER_PHONE)) {
            String supplierPhone = values.getAsString(BookEntry.COLUMN_BOOK_SUPPLIER_PHONE);
            if (supplierPhone == null) {
                throw new IllegalArgumentException("Requires valid name of supplier");
            }
        }
        
        // If there are no values to update, then don't try to update the database
        if (values.size() == 0) {
            return 0;
        }
        
        // Otherwise, get writable database to update the data
        SQLiteDatabase database = mBookStoreDbHelper.getWritableDatabase();
        
        // Perform the update on the database and get the number of rows affected
        int rowsUpdated = database.update(BookEntry.TABLE_NAME, values, selection, selectionArgs);
        
        // If 1 or more rows were updated, then notify all listeners that the data at the
        // given URI has changed
        if (rowsUpdated != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        
        // Return the number of rows updated
        return rowsUpdated;
    }
    
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // Get writeable database
        SQLiteDatabase database = mBookStoreDbHelper.getWritableDatabase();
        
        // Track the number of rows that were deleted
        int rowsDeleted;
        
        final int match = sUriMatcher.match(uri);
        
        switch (match) {
            case BOOKS:
                // Delete all rows that match the selection and selection args
                rowsDeleted = database.delete(BookEntry.TABLE_NAME, selection, selectionArgs);
                break;
            case BOOK_ID:
                // Delete a single row given by the ID in the URI
                selection = BookEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};
                rowsDeleted = database.delete(BookEntry.TABLE_NAME, selection, selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Deletion is not supported for " + uri);
        }
        
        // If 1 or more rows were deleted, then notify all listeners that the data at the
        // given URI has changed
        if (rowsDeleted != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        
        // Return the number of rows deleted
        return rowsDeleted;
    }
    
    @Override
    public String getType(Uri uri) {
        final int match = sUriMatcher.match(uri);
        switch (match) {
            case BOOKS:
                return BookEntry.CONTENT_LIST_TYPE;
            case BOOK_ID:
                return BookEntry.CONTENT_ITEM_TYPE;
            default:
                throw new IllegalStateException("Unknown URI " + uri + " with match " + match);
        }
    }
}
