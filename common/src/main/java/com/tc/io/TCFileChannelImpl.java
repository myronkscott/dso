/* 
 * The contents of this file are subject to the Terracotta Public License Version
 * 2.0 (the "License"); You may not use this file except in compliance with the
 * License. You may obtain a copy of the License at 
 *
 *      http://terracotta.org/legal/terracotta-public-license.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 *
 * The Covered Software is Terracotta Platform.
 *
 * The Initial Developer of the Covered Software is 
 *      Terracotta, Inc., a Software AG company
 */
package com.tc.io;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;

public class TCFileChannelImpl implements TCFileChannel {

  private final FileChannel channel;

  public TCFileChannelImpl(FileChannel channel) {
    this.channel = channel;
  }

  @Override
  public TCFileLock lock() throws IOException, OverlappingFileLockException {
    return new TCFileLockImpl(channel.lock());
  }

  @Override
  public void close() throws IOException {
    channel.close();
  }

  @Override
  public TCFileLock tryLock() throws IOException, OverlappingFileLockException {
    FileLock lock = channel.tryLock();
    if (lock != null) { return new TCFileLockImpl(lock); }
    return null;
  }

}
