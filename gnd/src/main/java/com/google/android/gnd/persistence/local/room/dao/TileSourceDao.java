/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.gnd.persistence.local.room.dao;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Transaction;
import com.google.android.gnd.persistence.local.room.entity.TileSourceEntity;
import com.google.android.gnd.persistence.local.room.relations.TileSourceWithOfflineAreas;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import java.util.List;

@Dao
public interface TileSourceDao extends BaseDao<TileSourceEntity> {

  @Query("SELECT * FROM tile_sources")
  Flowable<List<TileSourceEntity>> findAllOnceAndStream();

  @Query("SELECT * FROM tile_sources WHERE state = :state")
  Single<List<TileSourceEntity>> findByState(int state);

  @Query("SELECT * FROM tile_sources WHERE id = :id")
  Maybe<TileSourceEntity> findById(String id);

  @Query("SELECT * FROM tile_sources WHERE path = :path")
  Maybe<TileSourceEntity> findByPath(String path);

  @Transaction
  @Query("SELECT * FROM tile_sources")
  Maybe<List<TileSourceWithOfflineAreas>> getTileSourcesWithOfflineAreas();
}
