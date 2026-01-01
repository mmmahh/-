@echo off
echo Content-Type: text/html
echo.
echo ^<html^>
echo ^<body^>
echo ^<h1^>Windows CGI Test^</h1^>
echo ^<p^>REQUEST_METHOD: %REQUEST_METHOD%^</p^>
echo ^<p^>QUERY_STRING: %QUERY_STRING%^</p^>
echo ^<p^>Server Time: %TIME%^</p^>
echo ^</body^>
echo ^</html^>