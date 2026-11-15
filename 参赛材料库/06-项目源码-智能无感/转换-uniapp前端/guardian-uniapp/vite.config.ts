import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'
import fs from 'node:fs'
import path from 'node:path'

/**
 * 自动修复 project.private.config.json 的 urlCheck
 *
 * 背景：
 *   小程序开发阶段要访问 http://localhost:3000（非 https 且未在后台配域名），
 *   必须在【项目私有配置】里关闭域名校验 urlCheck:false。
 *
 *   但 uni-app 每次编译都会重新生成这个文件，把 urlCheck 重置掉，
 *   导致「网络异常」「列表空白」等问题反复出现。
 *
 * 做法：
 *   编译结束后自动写回 urlCheck:false。主配置与私有配置都写，双保险。
 */
function fixWeappUrlCheck() {
  const OUT_DIRS = ['dist/dev/mp-weixin', 'dist/build/mp-weixin']

  const PRIVATE_CONFIG = {
    description:
      '项目私有配置文件。此文件中的内容将覆盖 project.config.json 中的相同字段。' +
      '项目的改动优先同步到此文件中。',
    projectname: 'guardian-uniapp',
    setting: {
      compileHotReLoad: true,
      urlCheck: false,
      es6: true,
      enhance: true,
      postcss: true
    }
  }

  return {
    name: 'fix-weapp-urlcheck',
    apply: 'build',
    closeBundle() {
      for (const dir of OUT_DIRS) {
        const abs = path.resolve(process.cwd(), dir)
        if (!fs.existsSync(abs)) continue

        try {
          const privateFile = path.join(abs, 'project.private.config.json')
          fs.writeFileSync(privateFile, JSON.stringify(PRIVATE_CONFIG, null, 2), 'utf8')

          const mainFile = path.join(abs, 'project.config.json')
          if (fs.existsSync(mainFile)) {
            const main = JSON.parse(fs.readFileSync(mainFile, 'utf8'))
            main.setting = { ...(main.setting || {}), urlCheck: false }
            fs.writeFileSync(mainFile, JSON.stringify(main, null, 2), 'utf8')
          }

          console.log(`[fix-weapp-urlcheck] urlCheck=false -> ${dir}`)
        } catch (e) {
          console.warn(`[fix-weapp-urlcheck] ${dir} failed: ${e.message}`)
        }
      }
    }
  }
}

export default defineConfig({
  plugins: [uni(), fixWeappUrlCheck()],
  server: {
    port: 5176,
    proxy: {
      // H5 开发环境代理到 Java 后端
      '/api': {
        target: 'http://localhost:3000',
        changeOrigin: true
      }
    }
  }
})
