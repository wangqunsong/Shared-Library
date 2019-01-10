# Shared-Library
1. Jenkins通过Manage Jenkins » Configure System » Global Pipeline Libraries 的方式添加共享库;
2. 这些库将全局可用，系统中的任何Pipeline都可以利用这些库中实现的功能;
3. 通过配置SCM的方式，保证在每次构建时获取到指定Shared Library的最新代码。

