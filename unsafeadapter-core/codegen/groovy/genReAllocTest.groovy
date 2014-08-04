template = """
        /**
         * Tests a ##type## allocation, write, read, re-allocation and deallocation
         * @throws Exception thrown on any error
         */
        @Test
        public void testReallocated##Type##() throws Exception {
            ##type## address = -1;
            try {
                address = UnsafeAdapter.allocateMemory(##size##);
                ##type## value = nextPos##Type##();
                ##type## nextValue = nextPos##Type##();
                UnsafeAdapter.put##Type##(address, value);
                Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.get##Type##(address));
                Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.get##Type##(address));    
                if(UnsafeAdapter.getMemoryMBean().isTrackingEnabled()) {
                    Assert.assertEquals("Mem Total Alloc was unexpected", ##size##, UnsafeAdapter.getMemoryMBean().getTotalAllocatedMemory());
                } else {
                    Assert.assertEquals("Mem Total Alloc was unexpected", -1, UnsafeAdapter.getMemoryMBean().getTotalAllocatedMemory());
                }            
                address = UnsafeAdapter.reallocateMemory(address, ##size##*2);
                UnsafeAdapter.put##Type##(address + 8, nextValue);
                Assert.assertEquals("First Value was not [" + value + "]", value, UnsafeAdapter.get##Type##(address));
                Assert.assertEquals("First Value was not [" + value + "]", value, testUnsafe.get##Type##(address));    
                Assert.assertEquals("Second Value was not [" + nextValue + "]", nextValue, UnsafeAdapter.get##Type##(address + ##size##));
                Assert.assertEquals("Second Value was not [" + nextValue + "]", nextValue, testUnsafe.get##Type##(address + ##size##));    
                if(UnsafeAdapter.getMemoryMBean().isTrackingEnabled()) {
                    Assert.assertEquals("Mem Total Alloc was unexpected", ##size##*2, UnsafeAdapter.getMemoryMBean().getTotalAllocatedMemory());
                } else {
                    Assert.assertEquals("Mem Total Alloc was unexpected", -1, UnsafeAdapter.getMemoryMBean().getTotalAllocatedMemory());
                }            
            } finally {
                if(address!=-1) UnsafeAdapter.freeMemory(address);
                if(UnsafeAdapter.getMemoryMBean().isTrackingEnabled()) {
                    Assert.assertEquals("Mem Total Alloc was unexpected", 0, UnsafeAdapter.getMemoryMBean().getTotalAllocatedMemory());
                } else {
                    Assert.assertEquals("Mem Total Alloc was unexpected", -1, UnsafeAdapter.getMemoryMBean().getTotalAllocatedMemory());
                }
            }
        }
""";

pop = { type, size ->
    def clazz = Class.forName("java.lang.$type");
    def prim = clazz.TYPE.getName();
    return template.replace("##Type##", type).replace("##type##", prim).replace("##size##", "$size");
}

println pop("Integer", 4);

return null;