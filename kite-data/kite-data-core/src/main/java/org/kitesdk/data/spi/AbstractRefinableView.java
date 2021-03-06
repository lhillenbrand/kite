/*
 * Copyright 2013 Cloudera.
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

package org.kitesdk.data.spi;

import com.google.common.base.Predicate;
import org.kitesdk.data.Dataset;
import org.kitesdk.data.DatasetDescriptor;
import com.google.common.base.Objects;

import javax.annotation.concurrent.Immutable;

import org.kitesdk.data.RefinableView;
import org.kitesdk.data.View;

/**
 * A common View base class to simplify implementations of Views created from ranges.
 *
 * @param <E>
 *      The type of entities stored in the {@code Dataset} underlying this
 *      {@code View}.
 * @since 0.9.0
 */
@Immutable
public abstract class AbstractRefinableView<E> implements RefinableView<E> {

  protected final Dataset<E> dataset;
  protected final MarkerComparator comparator;
  protected final Constraints constraints;
  protected final Predicate<E> entityTest;

  // This class is Immutable and must be thread-safe
  protected final ThreadLocal<StorageKey> keys;

  protected AbstractRefinableView(Dataset<E> dataset) {
    this.dataset = dataset;
    final DatasetDescriptor descriptor = dataset.getDescriptor();
    if (descriptor.isPartitioned()) {
      this.comparator = new MarkerComparator(descriptor.getPartitionStrategy());
      this.keys = new ThreadLocal<StorageKey>() {
        @Override
        protected StorageKey initialValue() {
          return new StorageKey(descriptor.getPartitionStrategy());
        }
      };
    } else {
      this.comparator = null;
      this.keys = null;
    }
    this.constraints = new Constraints(dataset.getDescriptor().getSchema());
    this.entityTest = constraints.toEntityPredicate();
  }

  protected AbstractRefinableView(AbstractRefinableView<E> view, Constraints constraints) {
    this.dataset = view.dataset;
    this.comparator = view.comparator;
    this.constraints = constraints;
    this.entityTest = constraints.toEntityPredicate();
    // thread-safe, so okay to reuse when views share a partition strategy
    this.keys = view.keys;
  }

  protected abstract AbstractRefinableView<E> filter(Constraints c);

  @Override
  public Dataset<E> getDataset() {
    return dataset;
  }

  @Override
  public boolean deleteAll() {
    throw new UnsupportedOperationException(
        "This Dataset does not support bulk deletion");
  }

  /**
   * Returns an Iterable of non-overlapping {@link View} objects that partition
   * the underlying {@link org.kitesdk.data.Dataset} and cover this {@code View}.
   *
   * The returned {@code View} objects are implementation-specific, but should
   * represent reasonable partitions of the underlying {@code Dataset} based on
   * its layout.
   *
   * The data contained by the union of each {@code View} in the Iterable must
   * be a super-set of this {@code View}.
   *
   * Note that partitions are actual partitions under which data is stored.
   * Implementations are encouraged to omit any {@code View} that is empty.
   *
   * This method is intended to be used by classes like InputFormat, which need
   * to enumerate the underlying partitions to create InputSplits.
   *
   * @return
   *      An Iterable of the {@code View} that cover this {@code View}.
   * @throws IllegalStateException
   *      If the underlying {@code Dataset} is not partitioned.
   */
  public Iterable<View<E>> getCoveringPartitions() {
    throw new UnsupportedOperationException("This Dataset does not support " +
        "getCoveringPartitions.");
  }

  @Override
  public boolean includes(E entity) {
    return entityTest.apply(entity);
  }

  @Override
  public AbstractRefinableView<E> with(String name, Object... values) {
    return filter(constraints.with(name, values));
  }

  @Override
  public AbstractRefinableView<E> from(String name, Comparable value) {
    return filter(constraints.from(name, value));
  }

  @Override
  public AbstractRefinableView<E> fromAfter(String name, Comparable value) {
    return filter(constraints.fromAfter(name, value));
  }

  @Override
  public AbstractRefinableView<E> to(String name, Comparable value) {
    return filter(constraints.to(name, value));
  }

  @Override
  public AbstractRefinableView<E> toBefore(String name, Comparable value) {
    return filter(constraints.toBefore(name, value));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if ((o == null) || !Objects.equal(this.getClass(), o.getClass())) {
      return false;
    }

    AbstractRefinableView that = (AbstractRefinableView) o;
    return (Objects.equal(this.dataset, that.dataset) &&
        Objects.equal(this.constraints, that.constraints));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getClass(), dataset, constraints);
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
        .add("dataset", dataset)
        .add("constraints", constraints)
        .toString();
  }
}
