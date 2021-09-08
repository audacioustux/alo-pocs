(module
 (type $none_=>_none (func))
 (memory $0 0)
 (export "floyd" (func $assembly/index/floyd))
 (export "memory" (memory $0))
 (func $assembly/index/floyd
  (local $0 i32)
  (local $1 i32)
  (local $2 i32)
  i32.const 1
  local.set $2
  i32.const 1
  local.set $0
  loop $for-loop|0
   local.get $0
   i32.const 10
   i32.le_s
   if
    i32.const 1
    local.set $1
    loop $for-loop|1
     local.get $0
     local.get $1
     i32.ge_s
     if
      local.get $2
      i32.const 1
      i32.add
      local.set $2
      local.get $1
      i32.const 1
      i32.add
      local.set $1
      br $for-loop|1
     end
    end
    local.get $0
    i32.const 1
    i32.add
    local.set $0
    br $for-loop|0
   end
  end
 )
)
