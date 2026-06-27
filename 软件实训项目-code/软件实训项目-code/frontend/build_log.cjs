const { execSync } = require('child_process')
const fs = require('fs')
const path = require('path')
const cwd = __dirname
try {
  const out = execSync('npm run build', { cwd, encoding: 'utf-8', stdio: 'pipe', maxBuffer: 10 * 1024 * 1024 })
  fs.writeFileSync(path.join(cwd, 'build_output.log'), out)
  console.log('BUILD SUCCESS')
  console.log(out.slice(-500))
} catch (e) {
  const msg = (e.stdout || '') + '\n---STDERR---\n' + (e.stderr || '')
  fs.writeFileSync(path.join(cwd, 'build_output.log'), msg)
  console.log('BUILD FAILED')
  console.log(msg.slice(-2000))
  process.exit(e.status || 1)
}
