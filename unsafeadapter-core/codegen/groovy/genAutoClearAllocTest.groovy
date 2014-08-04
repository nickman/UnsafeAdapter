template = """
	/**
	 * Tests an auto de-allocated ##type## memory allocation 
	 * @throws Exception thrown on any error
	 */
	
	@Test	
	public void testAutoClearedAllocated##Type##() throws Exception {
		DefaultAssignableDeAllocateMe dealloc = new DefaultAssignableDeAllocateMe(1); 
		final long address = UnsafeAdapter.allocateMemory(##size"", dealloc);
		long value = nextPos##Type##();
		UnsafeAdapter.put##Type##(address, value);
		Assert.assertEquals("Value was not [" + value + "]", value, UnsafeAdapter.get##Type##(address));
		Assert.assertEquals("Value was not [" + value + "]", value, testUnsafe.get##Type##(address));
		validateAllocated(##size##, -1);
		log("MemoryBean State After Alloc: %s", UnsafeAdapter.getMemoryMBean().getState());
		dealloc = null;
		System.gc();
		sleep(100);
		log("MemoryBean State After Clear: %s", UnsafeAdapter.getMemoryMBean().getState());
		validateDeallocated(0, -1);		
	}

""";

pop = { type, size ->
    def clazz = Class.forName("java.lang.$type");
    def prim = clazz.TYPE.getName();
    return template.replace("##Type##", type).replace("##type##", prim).replace("##size##", "$size");
}

println pop("Boolean", 1);
println pop("Byte", 1);
println pop("Character", 2);
println pop("Short", 2);
println pop("Integer", 4);
println pop("Float", 4);
println pop("Double", 4);

return null;