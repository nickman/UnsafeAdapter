template = """
    /**
     * Tests a ##type## allocation, write, read and deallocation
     * @throws Exception thrown on any error
     */
    
    @Test
    public void testAllocated##Type##() throws Exception {
        final long address = UnsafeAdapter.allocateMemory(##size##);
        try {
            ##type## value = nextPos##Type##();
            UnsafeAdapter.put##Type##(address, value);
            Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.get##Type##(address));
            Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.get##Type##(address));    
            value = nextPos##Type##();
            UnsafeAdapter.put##Type##Volatile(address, value);
            Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.get##Type##(address));
            Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.get##Type##(address));    
            if(UnsafeAdapter.getMemoryMBean().isTrackingEnabled()) {
                Assert.assertEquals("Mem Total Alloc was unexpected", ##size##, UnsafeAdapter.getMemoryMBean().getTotalAllocatedMemory());
            } else {
                Assert.assertEquals("Mem Total Alloc was unexpected", -1, UnsafeAdapter.getMemoryMBean().getTotalAllocatedMemory());
            }            
        } finally {
            UnsafeAdapter.freeMemory(address);
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